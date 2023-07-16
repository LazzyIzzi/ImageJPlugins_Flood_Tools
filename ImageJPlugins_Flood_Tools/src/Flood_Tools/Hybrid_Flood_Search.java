package Flood_Tools;


//import java.awt.Color;
//import java.awt.Font;
import java.awt.*;
//import java.io.*;
import java.util.Arrays;

import ij.*;
import ij.process.*;
import ij.text.TextPanel;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.measure.*;

import jhd.FloodFill.HybridFloodFill;
import jhd.FloodFill.HybridFloodFill.FloodReport;
//import jhd.FloodFill.HybridFloodFill.PorosityReport;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.NumericField;

public class Hybrid_Flood_Search implements PlugInFilter, DialogListener
{
	ImagePlus imp;
	final static boolean doEDM = true;
	final static boolean noEDM = false;
	final static boolean doGDT = true;
	final static boolean noGDT = false;

	HybridFloodFill hff = new HybridFloodFill();
	FloodReport fldRpt = new FloodReport();
	String[] conChoices = HybridFloodFill.GetConnectivityChoices();
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff
	String mapType;
	double dryResVol;
	double dryUnresVol;
	double dryTotVol;
	final String resultTitle = "Hybrid Flood Results";
	final String plotTitle = "Hybrid Flood Plot";

	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	class DialogParams
	{
		public DialogParams() {};
		public double	floodInc;	//The minimum voxel value to flood
		//public double	floodVal;	//fill floodable voxels with this value
		public double	floodMin;	//fill floodable voxels with this value
		public double	floodMax;	//fill floodable voxels with this value
		public int		neighbors;	//6, 18, or 26 Connected
		public String	conChoice;	//"Face","Face & Edge","Face, Edge &Corners" touching neighbor voxels
		public boolean	showResults;//Display statistics of the flood
		public boolean	saveImages;
		public boolean	showBreakImage;
		public boolean	showPlot;
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
		DialogParams dp = new DialogParams();
		dp.floodInc = 0.01f;
		dp.conChoice = "Face, Edge &Corners";
		dp.neighbors = 26;
		dp.saveImages=false;
		dp.showBreakImage=false;
		dp.showPlot=true;

		mapType = imp.getProp("MapType");

		if(mapType==null || !mapType.equals("HybridPorosity") )
		{
			IJ.error("This plugin requires a Hybrid porosity image.");
			return null;
		}
		else
		{
			dp.floodMin = Float.valueOf(imp.getProp("unresMinPhi"));
			if(dp.floodMin==0) dp.floodMin = Float.valueOf(imp.getProp("resPoreMinR"));
			dp.floodMax = Float.valueOf(imp.getProp("resPoreMaxR"));
			dryResVol = Float.valueOf(imp.getProp("resPoreVol"));
			dryUnresVol = Float.valueOf(imp.getProp("unresPoreVol"));
			dryTotVol = dryResVol+dryUnresVol;
		}

		return dp;
	}

	//**********************************************************************************************
	
	NumericField floodDeltaNF;
	
	private DialogParams DoMyDialog(DialogParams dp)
	{		

		String	msg = "Hybrid Image Info:\n"
				+ "Maximum Resolved Pore Radius = " + imp.getProp("resPoreMaxR")+"\n"
				+ "Minimum Resolved Pore Radius = " + imp.getProp("resPoreMinR")+"\n"
				+ "Maximum Unresolved Porosity = " + imp.getProp("unresMaxPhi")+"\n"
				+ "Minimum Unresolved Porosity = " + imp.getProp("unresMinPhi");

		String unit = imp.getCalibration().getUnit();

		GenericDialog gd = new GenericDialog("Hybrid Flood Search");
		GenericDialogAddin gda = new GenericDialogAddin();
		gd.setInsets(0,0,0);
		gd.addMessage(msg,myFont,Color.BLACK);
		gd.addChoice("Connectivity",conChoices,dp.conChoice);
		gd.addNumericField("Stop at Search delta("+unit+")", dp.floodInc, 2);
		floodDeltaNF = gda.getNumericField(gd, null, "floodDelta");
		
		gd.addCheckbox("Save Images", dp.saveImages);
		gd.addCheckbox("Show breakthrough Image", dp.showBreakImage);
		gd.addCheckbox("Show Plot", dp.showPlot);
		gd.addHelp("https://lazzyizzi.github.io/DistanceMapPages/HybridFloodSearchScan.html");
		gd.addDialogListener(this);
		gd.setBackground(myColor);
		gd.showDialog();

		if(gd.wasCanceled()) return null;

		int choiceIndex = gd.getNextChoiceIndex();
		dp.conChoice = conChoices[choiceIndex];
		switch (choiceIndex)
		{
		case 0: dp.neighbors = 6; break;
		case 1: dp.neighbors = 18; break;
		case 2: dp.neighbors = 26; break;
		}
		dp.floodInc= (float)gd.getNextNumber();
		dp.saveImages = gd.getNextBoolean();
		dp.showBreakImage = gd.getNextBoolean();
		dp.showPlot = gd.getNextBoolean();

		return dp;
	}

	//**********************************************************************************************	
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
				double delta = floodDeltaNF.getNumber();
				switch(name)
				{
				case "floodDelta":
					if(Double.isNaN(delta) || delta <=0)
					{
						floodDeltaNF.getNumericField().setBackground(Color.red);
						dialogOK = false;
					}
					else
					{
						floodDeltaNF.getNumericField().setBackground(Color.white);
						dialogOK = true;
					}
					break;
				}				
			}
		}
		return dialogOK;
	}
	
	//**********************************************************************************************	

	public void run(ImageProcessor ip)
	{
		DialogParams dp = GetDefaultParams();

		String dir=null, basename=null,fileName=null;
		ImagePlus dupImp;
		Object[] oDupArr;
		int w = imp.getStack().getWidth();
		int h = imp.getStack().getHeight();
		int d = imp.getStack().getSize();

		dp = DoMyDialog(dp);

		if(dp!=null)
		{
			basename = imp.getTitle();
			int loc = basename.lastIndexOf(".");
			basename = basename.substring(0, loc) + "_";
			if(dp.saveImages) dir = IJ.getDirectory("Choose a Directory");

			Calibration cal = imp.getCalibration();
			double pw = cal.pixelWidth;
			double ph = cal.pixelHeight;
			double pd = cal.pixelDepth;
			String pu = cal.getUnit();

			IJ.showStatus("Testing Connectivity Limits");
			dupImp = imp.duplicate();
			oDupArr = dupImp.getStack().getImageArray();

			fldRpt = hff.hybridFloodFill(oDupArr,w,h,d,pw,ph,pd,pu,dp.floodMin, dp.neighbors,noEDM,doGDT);

			dupImp.close();
			boolean floodMinTest = fldRpt.floodStatistics.contact;
			showFloodResults(dp,fldRpt,dp.floodMin,"Not Saved");

			dupImp =imp.duplicate();
			oDupArr = dupImp.getStack().getImageArray();
			double testLowerLimit = dp.floodMax -.1f;

			fldRpt = hff.hybridFloodFill(oDupArr,w,h,d,pw,ph,pd,pu,testLowerLimit, dp.neighbors,noEDM,doGDT);

			dupImp.close();
			boolean floodMaxTest = fldRpt.floodStatistics.contact;
			showFloodResults(dp,fldRpt,testLowerLimit,"Not Saved");

			if(floodMinTest == false || floodMaxTest==true)
			{
				IJ.error("Volume connectivity not within test range");
				return;
			}				

			else //Bisection search for floodMin at breakthrough 
			{
				IJ.showStatus("Bisection Search");
				//A flood always has the floodMax, the largest pore size, as one limit.
				//We want to find the largest value of testLowerLimit that allows the flood to reach the back slice

				//Our first guess is at the midpoint
				double lowerLimit	= dp.floodMin;
				double upperLimit	= dp.floodMax;
				testLowerLimit	= (upperLimit+lowerLimit)/2.0;

				//We have already determined that the flood connects at floodMin
				//so we set our initial solution to that value.
				double brkMin = lowerLimit;
				boolean done = false;

				while(done == false)
				{
					dupImp = imp.duplicate();
					oDupArr = dupImp.getStack().getImageArray();

					fldRpt = hff.hybridFloodFill(oDupArr,w,h,d,pw,ph,pd,pu,testLowerLimit, dp.neighbors,noEDM,doGDT);

					if(dp.saveImages)
					{
						fileName= basename + IJ.d2s((double)testLowerLimit,4);
						IJ.saveAs(dupImp, "Tiff", dir+fileName+".tif");
						IJ.showStatus("Bisection Search");
					}
					dupImp.close();

					showFloodResults(dp,fldRpt,testLowerLimit,fileName);


					if(fldRpt.floodStatistics.contact)
					{
						lowerLimit = testLowerLimit;
						if(testLowerLimit>brkMin) brkMin =testLowerLimit;
					}
					else
					{
						upperLimit = testLowerLimit;
					}

					testLowerLimit	= (upperLimit+lowerLimit)/2.0; 						 
					if(upperLimit-lowerLimit < dp.floodInc) done = true;						
				}

				//Recalculate the breakthrough image
				dupImp = imp.duplicate();
				oDupArr = dupImp.getStack().getImageArray();
				fldRpt = hff.hybridFloodFill(oDupArr,w,h,d,pw,ph,pd,pu,brkMin, dp.neighbors,noEDM,doGDT);
				showFloodResults(dp,fldRpt,brkMin,fileName);

				//save the breakthrough image
				if(dp.saveImages)
				{
					fileName= basename + IJ.d2s((double)testLowerLimit,4);
					IJ.saveAs(dupImp, "Tiff", dir+fileName+"Brk.tif");
				}
				//display the breakthrough image
				if(dp.showBreakImage)
				{
					fileName= basename + IJ.d2s((double)testLowerLimit,4);
					dupImp.setTitle(fileName+"Brk.tif");
					dupImp.show();
					//Re-scale the display between the min and max distance of the entire stack
					float fmin=Float.MAX_VALUE,fmax=Float.MIN_VALUE;
					float val;				
					for(int k=0;k<d;k++)
					{
						float[] fData= (float[])oDupArr[k];
						for(int j=0;j<fData.length;j++)
						{
							val = fData[j];
							if(val< fmin) fmin=val;
							else if(val>fmax) fmax=val;								
						}
					}				
					dupImp.setDisplayRange(fmin, fmax);
					IJ.run("Fire");
				}

				if(dp.showPlot)
				{
					Plot searchPlot = prepSearchPlot();
					if(searchPlot!= null) showUpdatePlot(searchPlot);
				}
			}
		}
	}
	
	//***************************************************************************************
	
	private void showUpdatePlot(Plot plot)
	{
		PlotWindow plotWin;
		plotWin = (PlotWindow)WindowManager.getWindow(plot.getTitle());
		if(plotWin==null)
		{
			plot.show();
		}
		else plotWin.drawPlot(plot);		
	}	
	
	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	private Plot prepSearchPlot()
	{

		Plot plot = null;
		ResultsTable rt;						
		rt = ResultsTable.getResultsTable(resultTitle);
		if(rt!=null)
		{
			//Scan does not identify a unique "Breakthrough" radius
			//Differentiate flood radius contact by drawing a black
			//line outside the flood point
			String unit = imp.getCalibration().getXUnit();			
			rt.sort("Min R "+unit);
			//hack to force redraw after sort
			IJ.renameResults(resultTitle, "Temp");
			IJ.renameResults("Temp",resultTitle);			
	
			//All of this min max stuff because plot.setLimitsToFit(false) or true; does not work!!
			double[] minR = rt.getColumn("Min R "+unit);
			double[] fldCnt = rt.getColumn("Tot Flood "+unit+(char)0x0b3);
			double[] unResFldCnt = rt.getColumn("Unres Flood "+unit+(char)0x0b3);
			double[] resFldCnt = rt.getColumn("Res Flood "+unit+(char)0x0b3);

			String[] contact = rt.getColumnAsStrings("Breakthrough");
			int firstContact=0;
			boolean foundContact = false;
			for(int i=0;i<contact.length;i++)
			{
				if(contact[i].equals("False"))
					{
						firstContact=i;
						foundContact=true;
						break;
					}
			}
			if(foundContact==false) firstContact = contact.length-1;
			
			double[] minRbrk = Arrays.copyOfRange(minR,0,firstContact);
			double[] minR2 = Arrays.copyOfRange(minR,firstContact,minR.length);
			
			double[] fldCntBrk = Arrays.copyOfRange(fldCnt,0,firstContact);
			double[] fldCnt2 = Arrays.copyOfRange(fldCnt,firstContact,fldCnt.length);
			
			double[] unResFldCntBrk = Arrays.copyOfRange(unResFldCnt,0,firstContact);
			double[] unResFldCnt2 = Arrays.copyOfRange(unResFldCnt,firstContact,unResFldCnt.length);
			
			double[] resFldCntBrk = Arrays.copyOfRange(resFldCnt,0,firstContact);
			double[] resFldCnt2 = Arrays.copyOfRange(resFldCnt,firstContact,resFldCnt.length);

			plot = new Plot(plotTitle,"Min R "+unit,"Tot Flood "+unit+(char)0x0b3);
			plot.add("line", minRbrk, fldCntBrk);
			plot.setStyle(0,"red,red,1,line");
			plot.setLabel(0, "Tot Flood Brk "+unit+(char)0x0b3 );
			
			plot.add("line", minR2, fldCnt2);
			plot.setStyle(1,"black,black,1,line");
			plot.setLabel(1, "Tot Flood "+unit+(char)0x0b3 );

			plot.add("circle", minRbrk, unResFldCntBrk);
			plot.setStyle(2,"red,blue,1,circle");
			plot.setLabel(2, "Unresolved Brk "+unit+(char)0x0b3 );
			
			plot.add("circle", minR2, unResFldCnt2);
			plot.setStyle(3,"black,blue,1,circle");
			plot.setLabel(3, "Unresolved "+unit+(char)0x0b3 );

			plot.add("circle", minRbrk, resFldCntBrk);
			plot.setStyle(4,"red,green,1,circle");
			plot.setLabel(4, "Resolved Brk "+unit+(char)0x0b3 );
						
			plot.add("circle", minR2, resFldCnt2);
			plot.setStyle(5,"black,green,1,circle");
			plot.setLabel(5, "Resolved "+unit+(char)0x0b3 );

			plot.setColor(Color.black);
			plot.setLineWidth(1);

			plot.addLegend("", "top-right");
			plot.setLimitsToFit(true);
		}
			return plot;
	}

//	private Plot prepSearchPlot()
//	{
//		Plot searchPlot = null;
//		ResultsTable rt;						
//		rt = ResultsTable.getResultsTable(resultTitle);
//		String unit = imp.getCalibration().getXUnit();
//		double[] brkR = new double[1];
//		double[] brkCnt = new double[1];
//		brkR[0] = rt.getValue("Min R "+unit, rt.getCounter()-1);
//		brkCnt[0] = rt.getValue("Tot Flood "+unit+(char)0x0b3, rt.getCounter()-1);
//		rt.sort("Min R "+unit);
//		//hack to force redraw after sort
//		IJ.renameResults(resultTitle, "Temp");
//		IJ.renameResults("Temp",resultTitle);
//		if(rt!=null)
//		{
//			double[] minR = rt.getColumn("Min R "+unit);
//			double[] fldCnt = rt.getColumn("Tot Flood "+unit+(char)0x0b3);
//			double[] unResFldCnt = rt.getColumn("Unres Flood "+unit+(char)0x0b3);
//			double[] resFldCnt = rt.getColumn("Res Flood "+unit+(char)0x0b3);
//
//			searchPlot = new Plot(plotTitle,"Min R "+unit,"Flood Volume"+unit+(char)0x0b3);
//			searchPlot.add("circle", minR, fldCnt);
//			searchPlot.setStyle(0,"black,black,1,circle");
//			searchPlot.setLabel(0, "Tot Flood "+unit+(char)0x0b3 );
//
//			searchPlot.add("circle", minR, unResFldCnt);
//			searchPlot.setStyle(1,"black,blue,1,circle");
//			searchPlot.setLabel(1, "Unresolved "+unit+(char)0x0b3 );
//
//			searchPlot.add("circle", minR, resFldCnt);
//			searchPlot.setStyle(2,"black,green,1,circle");
//			searchPlot.setLabel(2, "Resolved "+unit+(char)0x0b3 );
//
//			searchPlot.add("circle", brkR, brkCnt);
//			searchPlot.setStyle(3,"red,red,1,circle");
//			searchPlot.setLabel(3, "Breakthough" );
//
//			searchPlot.setColor(Color.black);
//			searchPlot.setLineWidth(1);
//			searchPlot.addLegend("", "top-right");
//		}
//		return searchPlot;
//	}

	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	private void showFloodResults(DialogParams dp, FloodReport fldRpt, double testMin, String fileName)
	{
		ResultsTable floodResults;					

		String unit = imp.getCalibration().getXUnit();

		floodResults = ResultsTable.getResultsTable(resultTitle);		
		if(floodResults == null) floodResults = new ResultsTable();
		floodResults.setPrecision(5);
		floodResults.incrementCounter();
		if(dp.saveImages)floodResults.addValue("File Name ",fileName);		

		floodResults.addValue("Min R "+unit,testMin);
		floodResults.addValue("Connectivity", dp.neighbors);
		if(fldRpt.floodStatistics.contact == true) floodResults.addValue("Breakthrough","True");
		else floodResults.addValue("Breakthrough","False");

		floodResults.addValue("Res Flood "+unit+(char)0x0b3,fldRpt.afterFlood.resolvedPoreVolume);
		floodResults.addValue("Unres Flood "+unit+(char)0x0b3,fldRpt.afterFlood.unresolvedPoreVolume);
		floodResults.addValue("Tot Flood "+unit+(char)0x0b3,fldRpt.afterFlood.resolvedPoreVolume+fldRpt.afterFlood.unresolvedPoreVolume);

		floodResults.addValue("Res Sat% ",fldRpt.afterFlood.resolvedPoreVolume/dryResVol);
		floodResults.addValue("Unres Sat% ",fldRpt.afterFlood.unresolvedPoreVolume/dryUnresVol);
		floodResults.addValue("Tot Sat% ",(fldRpt.afterFlood.resolvedPoreVolume+fldRpt.afterFlood.unresolvedPoreVolume)/dryTotVol);

		floodResults.addValue("Cycles at Contact",fldRpt.floodStatistics.contactCycles);
		floodResults.addValue("Tot flood Cycles",fldRpt.floodStatistics.totalCycles);
		floodResults.addValue("Tort Mean", fldRpt.floodStatistics.meanTort);
		floodResults.addValue("Tort StdDev", fldRpt.floodStatistics.stdDevTort);

		floodResults.show(resultTitle);
		Window win = WindowManager.getWindow(resultTitle);
		TextPanel txtPnl = (TextPanel)win.getComponent(0);
		txtPnl.showRow(txtPnl.getLineCount());
	}
}

