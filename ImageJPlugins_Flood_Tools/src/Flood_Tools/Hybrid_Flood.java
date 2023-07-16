package Flood_Tools;


//import java.awt.AWTEvent;
//import java.awt.Color;
//import java.awt.Font;

import java.awt.*;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.measure.*;

import jhd.FloodFill.*;
import jhd.FloodFill.HybridFloodFill.FloodReport;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.*;


public class Hybrid_Flood implements PlugInFilter, DialogListener
{
	ImagePlus imp;
	HybridFloodFill hff = new HybridFloodFill();
	String[] conChoices = HybridFloodFill.GetConnectivityChoices();
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff
	String mapType;
	DialogParams dp = new DialogParams();

	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	class DialogParams
	{
		public DialogParams() {};
//		public float	floodVal;	//fill floodable voxels with this value
		public float	floodMin;	//fill floodable voxels with this value
		public float	floodMax;	//fill floodable voxels with this value
		public int		neighbors;	//6, 18, or 26 Connected
		public String	conChoice;	//"Face","Face & Edge","Face, Edge &Corners" touching neighbor voxels
		public boolean	doGDT;		
//		public boolean	doEDM;		//removed from dialog. EDM will be calculated for porosity images and skipped for Hybrid images
		public boolean	createNewImage;
		public boolean	showResults;//Display statistics of the flood
	}

	//**********************************************************************************************

	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_32+STACK_REQUIRED+NO_UNDO;
	}

	//**********************************************************************************************

	private DialogParams GetDefaultParams()
	{

		mapType = imp.getProp("MapType");
		

		if(mapType==null || !mapType.equals("HybridPorosity"))
		{
			IJ.error("This plugin requires a Hybrid porosity image.");
			return null;
		}
		else
		{
//			dp.floodVal=255;
			dp.conChoice = conChoices[2];
			dp.neighbors = 26;
			dp.doGDT = true;
			dp.showResults=true;
			dp.createNewImage = true;
			
			dp.floodMin = Float.valueOf(imp.getProp("unresMinPhi"));
			if(dp.floodMin==0) dp.floodMin = Float.valueOf(imp.getProp("resPoreMinR"));
			dp.floodMax=Float.valueOf(imp.getProp("resPoreMaxR"));
//			dp.doEDM=false;
			return dp;
		}
	}

	//**********************************************************************************************

	NumericField floodMinNF;
	
	private DialogParams DoMyDialog()
	{		
		String msg = "Hybrid Image Info:\n"
				+ "Maximum Resolved Pore Radius = " + imp.getProp("resPoreMaxR")+"\n"
				+ "Minimum Resolved Pore Radius = " + imp.getProp("resPoreMinR")+"\n"
				+ "Maximum Unresolved Porosity = " + imp.getProp("unresMaxPhi")+"\n"
				+ "Minimum Unresolved Porosity = " + imp.getProp("unresMinPhi");
		
		GenericDialogAddin gda = new GenericDialogAddin();
		
		GenericDialog gd = new GenericDialog("HybridFlood");
		gd.setInsets(0,0,0);
		gd.addMessage(msg,myFont,Color.BLACK);
		gd.addChoice("Connectivity",conChoices,dp.conChoice);
		gd.addNumericField("Flood Minimum radius ", dp.floodMin, 3);
		floodMinNF = gda.getNumericField(gd, null, "floodMin");
		gd.addCheckbox("Calculate Tortuosity", dp.doGDT);
		gd.addCheckbox("Show Metrics", dp.showResults);
		gd.addCheckbox("Create New Image", dp.createNewImage);
		
		gd.addHelp("https://lazzyizzi.github.io/DistanceMapPages/HybridFloodFill.html");
		gd.setBackground(myColor);
		gd.addDialogListener(this);
		gd.showDialog();

		if(gd.wasCanceled()) return null;

		int choiceIndex = gd.getNextChoiceIndex();
		dp.conChoice = conChoices[choiceIndex];
		dp.doGDT = gd.getNextBoolean();
		dp.showResults = gd.getNextBoolean();
		dp.createNewImage = gd.getNextBoolean();
		switch (choiceIndex)
		{
		case 0: dp.neighbors = 6; break;
		case 1: dp.neighbors = 18; break;
		case 2: dp.neighbors = 26; break;
		}
		dp.floodMin= (float)gd.getNextNumber();

		return dp;
	}
	//**********************************************************************************************	
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		boolean dialogOK=true;
		if(e!=null)
		{
			Object src = e.getSource();
			if(src instanceof TextField)
			{
				TextField tf = (TextField)src;
				String name = tf.getName();
				if(name.equals("floodMin"))
				{
					double val = floodMinNF.getNumber();
					if(val<=0 || val> dp.floodMax)
					{
						floodMinNF.getNumericField().setBackground(Color.RED);
						dialogOK = false;
					}
					else
					{
						floodMinNF.getNumericField().setBackground(Color.white);
						dialogOK=true;
					}
				}			
			}
		}
		return dialogOK;
	}

	//**********************************************************************************************

	public void run(ImageProcessor ip)
	{
		dp = GetDefaultParams();
		if(dp==null) return;

		HybridFloodFill hff = new HybridFloodFill();

		int w = imp.getStack().getWidth();
		int h = imp.getStack().getHeight();
		int d = imp.getStack().getSize();

		dp = DoMyDialog();
		if(dp!=null)
		{
			if(dp.createNewImage)
			{
				String title = imp.getTitle();
				//title.lastIndexOf(".");
				if(title.endsWith(".tif")) title=title.replace(".tif", "_Flood_"+dp.floodMin+".tif");
				else title = title + "_Flood_"+dp.floodMin;
				imp = imp.duplicate();
				imp.setTitle(title);
				imp.show();
			}
			Calibration cal = imp.getCalibration();
			float pw = (float)cal.pixelWidth;
			float ph = (float)cal.pixelHeight;
			float pd = (float)cal.pixelDepth;
			String pu = cal.getUnit();

			Object[] oImageArr = imp.getStack().getImageArray();

			FloodReport fldRpt = hff.hybridFloodFill(oImageArr,w,h,d,pw,ph,pd,pu,dp.floodMin, dp.neighbors,false,dp.doGDT);

			StackStatistics stats = new StackStatistics(imp);
			imp.setProp("MapType","HybridFlood");
			imp.setProp("FloodThreshold", dp.floodMin);
			imp.getProcessor().setMinAndMax(stats.min, stats.max);
			IJ.run("Fire");

			if(fldRpt!=null && dp.showResults)
			{
				ShowResults(dp,fldRpt,dp.floodMin);
			}
		}
	}
	
	//**********************************************************************************************	

	private void ShowResults(DialogParams dp, FloodReport fldRpt, double testMin)
	{		
		ResultsTable rt;
		
		String unit = imp.getCalibration().getXUnit();
		
		rt = ResultsTable.getResultsTable("Hybrid Flood Results");		
		if(rt == null) rt = new ResultsTable();
		rt.setPrecision(4);
		//rt.setDecimalPlaces(0, 4); //not effective???
		rt.incrementCounter();
		rt.setLabel(imp.getTitle(), rt.getCounter()-1);
		//pre-flood statistics
		rt.addValue("Condition", "Before Flood");
		rt.addValue("Flood Min " +unit, testMin);
		rt.addValue("Res Solid Cnt", fldRpt.beforeFlood.resolvedSolidVoxelCount);
		rt.addValue("Res Pore Cnt", fldRpt.beforeFlood.resolvedPoreVoxelCount);
		rt.addValue("Unres Cnt", fldRpt.beforeFlood.unresolvedVoxelCount);
		rt.addValue("Floodable VoxCnt", fldRpt.beforeFlood.floodableVoxelCount);
		
		rt.addValue("Res Pore Vol " +unit+(char)0x0b3, fldRpt.beforeFlood.resolvedPoreVolume);
		rt.addValue("Unres Pore Vol " +unit+(char)0x0b3, fldRpt.beforeFlood.unresolvedPoreVolume);
		rt.addValue("Res Phi%", fldRpt.beforeFlood.resolvedPorosity*100);
		rt.addValue("Unres Phi%", fldRpt.beforeFlood.unresolvedPorosity*100);
		rt.addValue("Total Phi%", fldRpt.beforeFlood.totalPorosity*100);
		
		rt.addValue("Flood Cycles", "NA");
		rt.addValue("Contact Cycles", "NA");
		rt.addValue("Mean Tort", "NA");
		rt.addValue("Tort stdDev","NA");

		//post-flood statistics
		rt.incrementCounter();
		rt.setLabel(imp.getTitle(), rt.getCounter()-1);
		rt.addValue("Condition", "Flood Accessed");
		rt.addValue("Flood Min " +unit, testMin);
		//The solid count is not changed by flooding
		rt.addValue("Res Solid Cnt", fldRpt.beforeFlood.resolvedSolidVoxelCount);
		rt.addValue("Floodable VoxCnt", fldRpt.afterFlood.floodableVoxelCount);
		rt.addValue("Res Pore Cnt", fldRpt.afterFlood.resolvedPoreVoxelCount);
		rt.addValue("Unres Cnt", fldRpt.afterFlood.unresolvedVoxelCount);
		
		rt.addValue("Res Pore Vol " +unit+(char)0x0b3, fldRpt.afterFlood.resolvedPoreVolume);
		rt.addValue("Unres Pore Vol " +unit+(char)0x0b3, fldRpt.afterFlood.unresolvedPoreVolume);
		rt.addValue("Res Phi%", fldRpt.afterFlood.resolvedPorosity*100);
		rt.addValue("Unres Phi%", fldRpt.afterFlood.unresolvedPorosity*100);
		rt.addValue("Total Phi%", fldRpt.afterFlood.totalPorosity*100);
		
		rt.addValue("Flood Cycles", fldRpt.floodStatistics.totalCycles);
		rt.addValue("Contact Cycles", fldRpt.floodStatistics.contactCycles);
		if(fldRpt.floodStatistics.contact) rt.addValue("Contact", "True");
		else  rt.addValue("Contact", "False");
		rt.addValue("Mean Tort", fldRpt.floodStatistics.meanTort);
		rt.addValue("Tort stdDev", fldRpt.floodStatistics.stdDevTort);
		
		rt.show("Hybrid Flood Results");
	}

}

