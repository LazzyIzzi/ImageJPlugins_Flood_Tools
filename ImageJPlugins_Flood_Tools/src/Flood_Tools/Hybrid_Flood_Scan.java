package Flood_Tools;


import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.util.Arrays;

import ij.*;
import ij.process.*;
import ij.text.TextPanel;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.measure.*;

import jhd.FloodFill.HybridFloodFill;
import jhd.FloodFill.HybridFloodFill.FloodReport;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.*;


public class Hybrid_Flood_Scan implements PlugInFilter, DialogListener
{
	ImagePlus imp;
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
		public double	floodMin;	//the smallest radius or porosity to flood
		public double	floodMax;	//the largest radius or porosity to flood
		public double	floodInc;	//Step size for the scan between min and max
		public int		neighbors;	//6, 18, or 26 Connected
		public String	conChoice;	//"Face","Face & Edge","Face, Edge &Corners" touching neighbor voxels
		public boolean	showResults;//Display statistics of the flood
		public boolean	saveImages;
//		public boolean	showBreakImage;
		public boolean	showPlot;
	}

	//**********************************************************************************************

	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_32+STACK_REQUIRED+NO_UNDO;
	}

	//**********************************************************************************************
	double floodMinLimit, floodMaxLimit;
	private DialogParams GetDefaultParams()
	{
		DialogParams dp = new DialogParams();
		dp.floodInc = 1.0f;
		dp.conChoice = "Face, Edge &Corners";
		dp.neighbors = 26;
		dp.saveImages=false;
		dp.showPlot=true;

		mapType = imp.getProp("MapType");

		if(mapType==null || !mapType.equals("HybridPorosity") )
		{
			IJ.error("This plugin requires a Hybrid porosity image.");
			return null;
		}
		else
		{
			floodMinLimit = Float.valueOf(imp.getProp("unresMinPhi"));
			dp.floodMin = floodMinLimit;

			floodMaxLimit = Float.valueOf(imp.getProp("resPoreMaxR"));
			dp.floodMax = floodMaxLimit;
			
			dryResVol = Float.valueOf(imp.getProp("resPoreVol"));
			dryUnresVol = Float.valueOf(imp.getProp("unresPoreVol"));
			dryTotVol = dryResVol+dryUnresVol;
		}

		return dp;
	}

	//**********************************************************************************************
	NumericField floodMinNF,floodMaxNF,floodIncNF;
	MessageField scanCntMF;
	
	private DialogParams DoMyDialog(DialogParams dp)
	{		

		String	msg = "Hybrid Image Info:\n"
					+ "Maximum Resolved Pore Radius = " + imp.getProp("resPoreMaxR")+"\n"
					+ "Minimum Resolved Pore Radius = " + imp.getProp("resPoreMinR")+"\n"
					+ "Maximum Unresolved Porosity = " + imp.getProp("unresMaxPhi")+"\n"
					+ "Minimum Unresolved Porosity = " + imp.getProp("unresMinPhi");
	
		GenericDialog gd = new GenericDialog("Hybrid Flood Scan");
		GenericDialogAddin gda = new GenericDialogAddin();
		gd.setInsets(0,0,0);
		gd.addMessage(msg,myFont,Color.BLACK);
		gd.addChoice("Connectivity",conChoices,dp.conChoice);
		gd.addNumericField("Flood Minimum pixel value", dp.floodMin, 3);
		floodMinNF = gda.getNumericField(gd, null, "floodMin");
		gd.addNumericField("Flood Maximum pixel value", dp.floodMax, 3);
		floodMaxNF = gda.getNumericField(gd, null, "floodMax");
		gd.addNumericField("Flood Increment", dp.floodInc, 3);
		floodIncNF = gda.getNumericField(gd, null, "floodInc");
		int scanCnt = (int)((dp.floodMax-dp.floodMin)/dp.floodInc)+1;
		gd.addMessage("Scan Count: " + scanCnt);
		scanCntMF = gda.getMessageField(gd, "scanCnt");
		gd.addCheckbox("Save Images", dp.saveImages);
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
		dp.floodMin= gd.getNextNumber();
		dp.floodMax= gd.getNextNumber();
		dp.floodInc= gd.getNextNumber();
		dp.saveImages = gd.getNextBoolean();
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
				double min = floodMinNF.getNumber();
				double max = floodMaxNF.getNumber();
				double inc = floodIncNF.getNumber();
						int scanCnt = (int)((max-min)/inc)+1;
				switch(name)
				{
				case "floodMax":
					//if(Double.isNaN(max) || max < min || max <=0 || max>floodMaxLimit)
					if(Double.isNaN(max) || max < min || max <=0 )
					{
						floodMaxNF.getNumericField().setBackground(Color.red);
						dialogOK = false;
					}
					else
					{
						floodMaxNF.getNumericField().setBackground(Color.white);						
						scanCntMF.setLabel("Scan Count: " + scanCnt);
						dialogOK = true;
					}
					break;
				case "floodMin":
					//if(Double.isNaN(min) || min > max || min <floodMinLimit)
					if(Double.isNaN(min) || min > max || min <=0)
					{
						floodMinNF.getNumericField().setBackground(Color.red);
						dialogOK = false;
					}
					else
					{
						floodMinNF.getNumericField().setBackground(Color.white);
						scanCntMF.setLabel("Scan Count: " + scanCnt);
						dialogOK = true;
					}
					break;
				case "floodInc":
					if(Double.isNaN(inc) || inc <=0)
					{
						floodIncNF.getNumericField().setBackground(Color.red);
						dialogOK = false;
					}
					else
					{
						floodIncNF.getNumericField().setBackground(Color.white);
						scanCntMF.setLabel("Scan Count: " + scanCnt);
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
		if(dp==null) return;

		String dir=null, basename=null,fileName=null;
		ImagePlus dupImp;
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

			// the main loop for scanning the volume connectivity
			for(double floodR = dp.floodMin; floodR <= dp.floodMax; floodR+=dp.floodInc)
			{
				dupImp =imp.duplicate();
				Object[] oDupArr = dupImp.getStack().getImageArray();
				fldRpt = hff.hybridFloodFill(oDupArr,w,h,d,pw,ph,pd,pu,floodR, dp.neighbors,false,doGDT);
				if(dp.saveImages)
				{
					fileName = basename + IJ.d2s((double)floodR,4);
					IJ.saveAs(dupImp, "Tiff", dir+fileName+".tif");
					IJ.showStatus("Scanning R="+floodR);
				}
				dupImp.close();
				showFloodResults(dp,fldRpt,floodR,fileName);
			}	

			if(dp.showPlot)
			{
				Plot scanPlot = prepScanPlot();
				if(scanPlot!=null) showUpdatePlot(scanPlot);
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

	private Plot prepScanPlot()
	{

		Plot scanPlot = null;
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

			scanPlot = new Plot(plotTitle,"Min R "+unit,"Tot Flood "+unit+(char)0x0b3);
			scanPlot.add("line", minRbrk, fldCntBrk);
			scanPlot.setStyle(0,"red,red,1,line");
			scanPlot.setLabel(0, "Tot Flood Brk "+unit+(char)0x0b3 );
			
			scanPlot.add("line", minR2, fldCnt2);
			scanPlot.setStyle(1,"black,black,1,line");
			scanPlot.setLabel(1, "Tot Flood "+unit+(char)0x0b3 );

			scanPlot.add("circle", minRbrk, unResFldCntBrk);
			scanPlot.setStyle(2,"red,blue,1,circle");
			scanPlot.setLabel(2, "Unresolved Brk "+unit+(char)0x0b3 );
			
			scanPlot.add("circle", minR2, unResFldCnt2);
			scanPlot.setStyle(3,"black,blue,1,circle");
			scanPlot.setLabel(3, "Unresolved "+unit+(char)0x0b3 );

			scanPlot.add("circle", minRbrk, resFldCntBrk);
			scanPlot.setStyle(4,"red,green,1,circle");
			scanPlot.setLabel(4, "Resolved Brk "+unit+(char)0x0b3 );
						
			scanPlot.add("circle", minR2, resFldCnt2);
			scanPlot.setStyle(5,"black,green,1,circle");
			scanPlot.setLabel(5, "Resolved "+unit+(char)0x0b3 );

			scanPlot.setColor(Color.black);
			scanPlot.setLineWidth(1);

			scanPlot.addLegend("", "top-right");
			scanPlot.setLimitsToFit(true);
		}
			return scanPlot;
	}

//	private void plotFloodResults()
//	{
//
//		ResultsTable rt;						
//		rt = ResultsTable.getResultsTable(resultTitle);
//		if(rt!=null)
//		{
//			String[] contact = rt.getColumnAsStrings("Breakthrough");
//			//get "True" counts
//			int contactTrueCnt=0;
//			for(int i=0;i<contact.length;i++)
//			{
//				if(contact[i].equals("True")) contactTrueCnt++;
//			}
//
//			//All of this min max stuff because plot.setLimitsToFit(false) or true; does not work!!
//			double[] contactMinR = new double[contactTrueCnt];
//			double[] contactFldCnt = new double[contactTrueCnt];
//			double[] noContactMinR = new double[contact.length-contactTrueCnt];
//			double[] noContactFldCnt = new double[contact.length-contactTrueCnt];
//
//			double maxR=Float.MIN_VALUE;
//			double minR=Float.MAX_VALUE;
//
//			double maxCnt=Float.MIN_VALUE;
//			double minCnt=Float.MAX_VALUE;
//			String unit = imp.getCalibration().getXUnit();
//
//			for(int i = 0, t=0, f=0; i<contact.length;i++)
//			{
//				if(contact[i].equals("True"))
//				{
//					contactMinR[t] = rt.getValue("Min R "+unit, i);
//					if(contactMinR[t] < minR) minR = contactMinR[t];
//					if(contactMinR[t] > maxR) maxR = contactMinR[t];
//					contactFldCnt[t] = rt.getValue("Tot Flood "+unit+(char)0x0b3, i);	
//					if(contactFldCnt[t] < minCnt) minCnt = contactFldCnt[t];
//					if(contactFldCnt[t] > maxCnt) maxCnt = contactFldCnt[t];
//					t++;
//				}
//				else
//				{
//					noContactMinR[f] = rt.getValue("Min R "+unit, i);
//					if(noContactMinR[f] < minR) minR = noContactMinR[f];
//					if(noContactMinR[f] > maxR) maxR = noContactMinR[f];
//					noContactFldCnt[f] = rt.getValue("Tot Flood "+unit+(char)0x0b3, i);	
//					if(noContactFldCnt[f] < minCnt) minCnt = noContactFldCnt[f];
//					if(noContactFldCnt[f] > maxCnt) maxCnt = noContactFldCnt[f];
//					f++;								
//				}
//			}
//
//			Plot plot = new Plot(resultTitle,"Min R "+unit,"Tot Flood "+unit+(char)0x0b3);
//			plot.addPoints(noContactMinR, noContactFldCnt, Plot.CIRCLE);
//			plot.setStyle(0,"black,black,1,circle");
//			
//			plot.addPoints(contactMinR, contactFldCnt, Plot.CIRCLE);
//			plot.setStyle(1,"red,red,1,circle");
//			plot.setLimits(minR,maxR,minCnt, maxCnt);
//			plot.show();
//		}
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

