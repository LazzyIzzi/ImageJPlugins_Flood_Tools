package Flood_Tools;

import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;

import jhd.FloodFill.FloodFill;
import jhd.FloodFill.Offsets.PointDesc;
import jhd.ImageJAddins.*;
import jhd.ImageJAddins.GenericDialogAddin.*;
//***********************************************************************************************

public class Flood_Fill_3D implements PlugInFilter, DialogListener {

	private class DialogParams
	{
		int neighbors;
		double floodMin;
		double floodMax;
		double fillVal;
		boolean showVoxCnt;
	}

	NumericField  floodMinNF, floodMaxNF, floodValNF;
	ImagePlus imp;
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff

	//***********************************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_8G+DOES_16+DOES_32+STACK_REQUIRED+NO_UNDO;
	}

	//***********************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		DialogParams dp = doMyDialog();
		if(dp!=null) doMyRoutine(dp);		
	}

	//***********************************************************************************************

	private DialogParams doMyDialog()
	{
		Roi roi = imp.getRoi();
		if(roi==null )
		{
			IJ.error("FloodFill_Test", "Please use a Point or Freeline roi tool\n"
					+ " to make a flood seed selection");
			return null;
		}
		else
		{
			int roiType = roi.getType();
			if(roiType!=Roi.POINT &&  roiType!=Roi.FREELINE)
			{
				IJ.error("FloodFill_Test", "Please use a Point or Freeline roi tool\n"
						+ " to make a flood seed selection");
				return null;
			}
		}
		
		DialogParams dp = new DialogParams();

		GenericDialog gd = new GenericDialog("Flood Fill 3D");
		GenericDialogAddin gda = new GenericDialogAddin();
		String[] conChoices = FloodFill.GetConnectivityChoices();

		//StackStatistics call depends on the current ROI
		imp.resetRoi();
		ImageStatistics stkStats = new StackStatistics(imp);
		imp.setRoi(roi,true);
		
		gd.addMessage("Flood voxel values between Min and Max.\n"
				+ "The fill value must not be between Min and Max.",myFont);
		gd.addChoice("Voxel Connectivity", conChoices, conChoices[2]);
		
		gd.addNumericField("Flood Min limit", stkStats.min);
		floodMinNF = gda.getNumericField(gd, null, "floodMin");
		
		gd.addNumericField("Flood Max limit", stkStats.max);
		floodMaxNF = gda.getNumericField(gd, null, "floodMax");
				
		gd.addNumericField("Flood Fill Voxel value", 255);
		floodValNF = gda.getNumericField(gd, null, "floodVal");
		
		gd.addCheckbox("Show Flooded Count", true);
		gd.addHelp("https://lazzyizzi.github.io/FloodFill.html");
		gd.addDialogListener(this);
		gd.setBackground(myColor);
		gd.showDialog();

		if(gd.wasCanceled()) return null;

		dp.neighbors = FloodFill.GetConnectivityValue(gd.getNextChoice());
		dp.floodMin = gd.getNextNumber();
		dp.floodMax = gd.getNextNumber();
		dp.fillVal = gd.getNextNumber();
		dp.showVoxCnt = gd.getNextBoolean();

		return dp;
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		boolean dialogOK = true;
		
		if(e!=null)
		{
			Object src = e.getSource();
			if(src instanceof TextField)
			{
				TextField tf = (TextField)src;
				String name = tf.getName();
				//floodMin, floodMax, and floodVal must all be in range
				//and floodVal nust lie between floodMin and floodMax
				double val = floodValNF.getNumber();
				double min = floodMinNF.getNumber();
				double max = floodMaxNF.getNumber();
				int bitDepth = imp.getBitDepth();
				switch(name)
				{
				case "floodMin":
					dialogOK = isInRange(bitDepth,min);
					if(dialogOK) floodMinNF.getNumericField().setBackground(Color.white);						
					else floodMinNF.getNumericField().setBackground(Color.red);
					break;
				case "floodMax":
					dialogOK = isInRange(bitDepth,max);
					if(dialogOK) floodMaxNF.getNumericField().setBackground(Color.white);						
					else floodMaxNF.getNumericField().setBackground(Color.red);
					break;
				case "floodVal":
					val = floodValNF.getNumber();
					min = floodMinNF.getNumber();
					max = floodMaxNF.getNumber();
					if((val>max || val<min) && isInRange(bitDepth,val))
					{
						floodValNF.getNumericField().setBackground(Color.white);
						dialogOK = true;
					}
					else
					{
						floodValNF.getNumericField().setBackground(Color.red);
						dialogOK = false;						
					}						
					break;					
				}
			}
		}
		return dialogOK;
	}

	//***********************************************************************************************

	private boolean isInRange(int bitDepth,double val)
	{
		boolean rangeOK=true;
		switch(bitDepth)
		{
		case 32:
			if(val>Float.MAX_VALUE || val<Float.MIN_VALUE)
			{
				rangeOK=false;								
			}
			break;
		case 16:
			if(val>65535 || val<0)
			{
				rangeOK=false;								
			}
			break;
		case 8:
			if(val>255 || val<0)
			{
				rangeOK=false;								
			}
			break;
		default:
			rangeOK=false;								
			break;
		}
		
		return rangeOK;
	}
	//***********************************************************************************************

	private void doMyRoutine(DialogParams dp)
	{
		int imgWidth = imp.getWidth();
		int imgHeight = imp.getHeight();
		int imgDepth = imp.getNSlices();
		Roi roi = imp.getRoi();
		FloodFill floodFill = new FloodFill();		
		List<PointDesc> seeds = new ArrayList<>();
		int bitDepth = imp.getBitDepth();
		
		//do the flooding using a float image to avoid lack of unsigned primitive types
		//The library only supports signed primitives
		ImageConverter imgConv = new ImageConverter(imp);		
		imgConv.convertToGray32();		
		Object[] oImageArr = imp.getStack().getImageArray();
		
		int floodbleCnt=0;		
		for(int i=0;i<imp.getStackSize();i++)
		{
			float[] slice = (float[])oImageArr[i];
			for(int j=0;j<slice.length;j++)
			{
				if(slice[j]<=dp.floodMax && slice[j]>=dp.floodMin) floodbleCnt++;
			}			
		}

		int voxelsFlooded=0;
		switch(roi.getType())
		{
		case Roi.POINT:
			Polygon p = roi.getPolygon();
			PointRoi pRoi = (PointRoi)roi;
			for(int i=0;i<p.npoints;i++)
			{
				seeds.add(new PointDesc(p.xpoints[i],p.ypoints[i],pRoi.getPointPosition(i)-1));
			}
			voxelsFlooded = floodFill.grayFloodFill3D(oImageArr, imgWidth, imgHeight, imgDepth,  dp.neighbors,  dp.floodMin,  dp.floodMax, dp.fillVal, seeds);		
			break;
		case Roi.FREELINE:
			int slice = imp.getSlice();
			p = roi.getPolygon();
			for(int i =0;i<p.npoints;i++)
			{
				seeds.add(new PointDesc(p.xpoints[i],p.ypoints[i],slice));
			}
			voxelsFlooded = floodFill.grayFloodFill3D(oImageArr, imgWidth, imgHeight, imgDepth,  dp.neighbors,  dp.floodMin,  dp.floodMax, dp.fillVal, seeds);
			break;
		default:
			IJ.error("FloodFill_Test", "Please use a Point or Freeline roi tool to make a selection");
			break;
		}

		//convert back to initial type
		switch(bitDepth)
		{
			case 8: imgConv.convertToGray8();break;
			case 16:imgConv.convertToGray16();break;
		}
		
		imp.updateAndDraw();
		IJ.run(imp, "Enhance Contrast", "saturated=0.35");
		
		if(dp.showVoxCnt)
		{
			ResultsTable rt = ResultsTable.getResultsTable("FloodFill 3D Results");
			if(rt==null) rt = new ResultsTable();
			rt.incrementCounter();
			rt.setPrecision(4);
			rt.addValue("Image", imp.getTitle());
			rt.addValue("Flood Min", dp.floodMin);
			rt.addValue("Flood Max", dp.floodMax);
			rt.addValue("Fill Value", dp.fillVal);
			rt.addValue("Connectivity",dp.neighbors);
			rt.addValue("Flooded", voxelsFlooded);
			rt.addValue("Not Flooded", floodbleCnt-voxelsFlooded);
			rt.addValue("% Flooded", 100.0*voxelsFlooded/floodbleCnt);
			rt.addValue("%Not Flooded", 100.0*(floodbleCnt-voxelsFlooded)/floodbleCnt);
			rt.show("FloodFill 3D Results");
		}
	}


}
