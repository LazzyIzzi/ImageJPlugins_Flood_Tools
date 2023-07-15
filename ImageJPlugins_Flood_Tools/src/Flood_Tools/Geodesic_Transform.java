package Flood_Tools;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Color;
//import java.awt.Component;
import java.awt.Font;
import java.awt.Polygon;
//import java.util.Vector;
import java.util.ArrayList;
import java.util.List;

import ij.IJ;
import ij.gui.*;
import ij.measure.Calibration;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.*;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;

//import jhd.DistanceMaps.libJ8.*;
import jhd.FloodFill.GDT3D;
import jhd.FloodFill.Offsets.PointDesc;
import jhd.ImageJAddins.GenericDialogAddin.*;
import jhd.ImageJAddins.GenericDialogAddin;

public class Geodesic_Transform implements PlugInFilter, DialogListener
{

	public Geodesic_Transform() {
		// TODO Auto-generated constructor stub
	}

	ImagePlus imp;
	GDT3D myGDT = new GDT3D();//uses pixel sizes
	GenericDialogAddin gda = new GenericDialogAddin();
	
	String[] destChoices3D = {"3D new Image","3D in Place","2D new Image","2D in Place", "2D this slice new image"};
	String[] destChoices2D = {"2D new Image","2D in Place"};
	String[] seedChoices2D = null;
	String[] seedChoices3D = null;
	String[] outputChoices = null;
	
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff


	//*********************************************************************************************

	class DialogParams
	{
		public String floodChoice;
		public String seedChoice;
		public String taskChoice;
		public String destChoice;
		public boolean 	useSize;
	}

	//*********************************************************************************************

	class SlicePoints
	{
		int sliceNum;
		public int[] jRow;
		public int[] iCol;
	}

	//*********************************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_8G + DOES_16 + DOES_32;			
	}

	//*********************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		
		ImageStack inStack;//, outStack;
		SlicePoints[] mySlicePts;

		inStack = imp.getStack();
		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = inStack.getSize();
		int i;
		
		int[] colPts=null,rowPts=null,slicePts=null;

		DialogParams dp = DoMyDialog();

		if(dp!=null)
		{
			if(ValidateParams(imp,dp))
			{
				if(dp.seedChoice=="Point(s)")
				{
					Roi roi= imp.getRoi();
					if(roi.getType()==Roi.POINT)
					{
						PointRoi pRoi = (PointRoi) roi;
						Polygon p = roi.getPolygon();
						colPts = new int[p.npoints];
						rowPts = new int[p.npoints];
						slicePts = new int[p.npoints];

						for(i = 0; i<p.npoints;i++)
						{
							colPts[i] = p.xpoints[i];
							rowPts[i] = p.ypoints[i];
							slicePts[i] = pRoi.getPointPosition(i)-1;
						}
					}		
				}
				
				float pixWidth=1,pixHeight=1,pixDepth=1;
				if(dp.useSize)
				{
					Calibration impCal = imp.getCalibration();
					pixWidth = (float)impCal.pixelWidth;
					pixHeight = (float)impCal.pixelHeight;
					pixDepth = (float)impCal.pixelDepth;
				}


				String title;
				title = imp.getTitle();
				ImagePlus myImp = null;
				ImageStack myStack;
				ImageConverter imgConvert;
				ImageConverter.setDoScaling(false);			
				//float[] myArrf;
				Object[] oData3D;
				Object oData2D;
				
				//long start = System.nanoTime();
				
				switch(dp.destChoice)
				{
				case "3D new Image":
					myImp = imp.duplicate();
					imgConvert = new ImageConverter(myImp);				
					imgConvert.convertToGray32();
					myStack = myImp.getStack();
					oData3D = myStack.getImageArray();
					//myArrf = inStack.getVoxels(0, 0, 0, width, height, depth, null);
					IJ.showStatus("Run " + dp.taskChoice +  " " + dp.destChoice + " " + dp.floodChoice+ " from " + dp.seedChoice);		
					switch(dp.taskChoice)
					{
					case "Geodesic Distance":
						myGDT.gdt3D(oData3D, width, height, depth, pixWidth, pixHeight, pixDepth, dp.floodChoice, dp.seedChoice, colPts,rowPts,slicePts);
						break;
					case "Tortuosity":
						myGDT.tort3D(oData3D, width, height, depth, pixWidth, pixHeight, pixDepth, dp.floodChoice, dp.seedChoice, colPts,rowPts,slicePts);
						break;
					}
					myImp.setTitle(dp.taskChoice + "_3D");
					myImp.show();
					break;
				case "3D in Place":
					imgConvert = new ImageConverter(imp);				
					imgConvert.convertToGray32();
					inStack = imp.getStack();// required refresh after conversion
					oData3D = inStack.getImageArray();

					IJ.showStatus("Run " + dp.taskChoice +  " " + dp.destChoice + " " + dp.floodChoice+ " from " + dp.seedChoice);		
					switch(dp.taskChoice)
					{
					case "Geodesic Distance":
						myGDT.gdt3D(oData3D, width, height, depth, pixWidth, pixHeight, pixDepth, dp.floodChoice, dp.seedChoice, colPts,rowPts,slicePts);
						break;
					case "Tortuosity":
						myGDT.tort3D(oData3D, width, height, depth,  pixWidth, pixHeight, pixDepth,dp.floodChoice, dp.seedChoice, colPts,rowPts,slicePts);
						break;
					}
					imp.setTitle(title + "_3D");
					myImp = imp;
					break;
				case"2D new Image":// this case needs to handle the point location in each slice					
					myImp = imp.duplicate();
					imgConvert = new ImageConverter(myImp);				
					imgConvert.convertToGray32();

					if(dp.seedChoice=="Point(s)")
					{//just do the slices with points in them
						mySlicePts = GetSlicePoints(imp);
						if(mySlicePts != null)
						{
							myStack = myImp.getStack();
							IJ.showStatus("Run " + dp.taskChoice +  " " + dp.destChoice + " " + dp.floodChoice+ " from " + dp.seedChoice);		
							for(i=0;i<mySlicePts.length;i++)
							{
								if(myStack.getSize() > 1) oData2D = myStack.getPixels(mySlicePts[i].sliceNum);
								else oData2D = myStack.getPixels(1);

								switch(dp.taskChoice)
								{
								case "Geodesic Distance":
									myGDT.gdt2D(oData2D, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice, mySlicePts[i].iCol,mySlicePts[i].jRow);
									break;
								case "Tortuosity":
									myGDT.tort2D(oData2D, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice, mySlicePts[i].iCol,mySlicePts[i].jRow);
									break;
								}
							}
							myImp.setSlice(mySlicePts[0].sliceNum);
						}
					}
					else
					{//Do all of the slices
						myStack = myImp.getStack();
						IJ.showStatus("Run " + dp.taskChoice +  " " + dp.destChoice + " " + dp.floodChoice+ " from " + dp.seedChoice);		
						for(i=1;i<=myStack.size();i++)
						{
							oData2D =  myStack.getPixels(i);
							switch(dp.taskChoice)
							{
							case "Geodesic Distance":
								myGDT.gdt2D(oData2D, width, height, pixWidth, pixHeight,dp.floodChoice, dp.seedChoice,null,null);
								break;
							case "Tortuosity":								//myGDT.tort2D(myArrf, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice,null,null);
								myGDT.tort2D(oData2D, width, height, pixWidth, pixHeight,dp.floodChoice, dp.seedChoice,null,null);
								break;
							}
						}					
					}
					myImp.setTitle(dp.taskChoice + "_2D");
					myImp.show();											
					break;
				case"2D in Place":
					imgConvert = new ImageConverter(imp);				
					imgConvert.convertToGray32();

					//This code succeeds when mySlicePts[i].sliceNum = 1
					if(dp.seedChoice=="Point(s)")
					{//just do the slices with points in them
						mySlicePts = GetSlicePoints(imp);
						if(mySlicePts != null)
						{
							myStack = imp.getStack();

							IJ.showStatus("Run " + dp.taskChoice +  " " + dp.destChoice + " " + dp.floodChoice+ " from " + dp.seedChoice);		
							for(i=0;i<mySlicePts.length;i++)
							{
								if(myStack.getSize() > 1) oData2D = myStack.getPixels(mySlicePts[i].sliceNum);
								else oData2D = myStack.getPixels(1);
								switch(dp.taskChoice)
								{
								case "Geodesic Distance":
									myGDT.gdt2D(oData2D, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice, mySlicePts[i].iCol,mySlicePts[i].jRow);
									break;
								case "Tortuosity":
									myGDT.tort2D(oData2D, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice, mySlicePts[i].iCol,mySlicePts[i].jRow);
									break;
								}
							}					
							imp.setSlice(mySlicePts[0].sliceNum);
							myImp = imp;
						}
					}
					else
					{
						myStack = imp.getStack();						
						IJ.showStatus("Run " + dp.taskChoice +  " " + dp.destChoice + " " + dp.floodChoice+ " from " + dp.seedChoice);		
						for(i=1;i<=myStack.size();i++)
						{
							oData2D = myStack.getPixels(i);
							switch(dp.taskChoice)
							{
							case "Geodesic Distance":
								myGDT.gdt2D(oData2D, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice,null,null);
								break;
							case "Tortuosity":
								myGDT.tort2D(oData2D, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice,null,null);
								break;
							}
						}					
					}
					imp.setTitle(dp.taskChoice + "_2D");
					myImp=imp;
					break;
				case "2D this slice new image":
					IJ.run("Duplicate...", " ");
					myImp = IJ.getImage(null);
					imgConvert = new ImageConverter(myImp);				
					imgConvert.convertToGray32();
					ImageProcessor myIp = myImp.getProcessor();
					oData2D =  myIp.getPixels();
					switch(dp.taskChoice)
					{
					case "Geodesic Distance":
						myGDT.gdt2D(oData2D, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice, colPts,rowPts);
						break;
					case "Tortuosity":
						myGDT.tort2D(oData2D, width, height, pixWidth, pixHeight, dp.floodChoice, dp.seedChoice, colPts,rowPts);
						break;
					}
					myImp.setTitle(dp.taskChoice + "_2D");
					myImp.show();
					break;
				}
				
				//long end = System.nanoTime();
				//double nSecs = (double)( end-start)*1e-9;
				//String secStr = String.format("%.4f" + " seconds", nSecs );  			
				//IJ.showStatus("GDT, " + dp.taskChoice +", " + dp.destChoice + ", " + dp.seedChoice + secStr);

				myImp.setProp("GeodesicType", dp.taskChoice);
				myImp.setProp("GeodesicSource", dp.destChoice);
				myImp.setProp("GeodesicSeed", dp.seedChoice);
				//If there is a point ROI ce.stretchHistogram(myImp.getProcessor(), 0.35); fails
				//Passing the stats does the stretch only on the current slice
				myImp.show();
				
				//Re-scale the display between the min and max distance of the entire stack
				float fmin=Float.MAX_VALUE,fmax=Float.MIN_VALUE;
				float val;
				Object[] outData = myImp.getStack().getImageArray();				
				for(i=0;i<myImp.getNSlices();i++)
				{
					float[] fData= (float[])outData[i];
					for(int j=0;j<fData.length;j++)
					{
						val = fData[j];
						if(val< fmin) fmin=val;
						else if(val>fmax) fmax=val;								
					}
				}				
				myImp.setDisplayRange(fmin, fmax);
				IJ.run("Fire");
			}
		}
	}

	//*********************************************************************************************
	ChoiceField seedChoicesCF,outputChoicesCF;
	RadioButtonField valToProcessRBF, processToRunRBF;
	CheckboxField usePixelSizeCBF;
	
	private DialogParams DoMyDialog() {

		String[] seedChoices;
		seedChoices2D = myGDT.getSeedChoices2D();
		seedChoices3D = myGDT.getSeedChoices3D();

		if(imp.getStackSize() == 1)
		{
			outputChoices = destChoices2D;
			seedChoices = seedChoices2D;
		}
		else
		{
			outputChoices = destChoices3D;			
			seedChoices = seedChoices3D;
		}

		String[] floodChoices = myGDT.getMapChoices(); //{"Map 0","Map 255"};
		String[] taskChoices = {"Geodesic Distance","Tortuosity"};

		DialogParams dp = new DialogParams();

		GenericDialog gd = new GenericDialog("Geodesic Transform");
		gd.addDialogListener(this);

		gd.addMessage("Convert binary image to Geodesic Distance or Tortuosity.",myFont,Color.BLACK);
		
		gd.addRadioButtonGroup("Value to process", floodChoices, 1, 2, floodChoices[0]);
		valToProcessRBF = gda.getRadioButtonField(gd, null, "valToProcess");
		
		gd.addRadioButtonGroup("Process to run", taskChoices, 1, 2, taskChoices[0]);
		processToRunRBF = gda.getRadioButtonField(gd, null, "processToRun");
		
		gd.addChoice("Output",outputChoices,outputChoices[0]);
		outputChoicesCF = gda.getChoiceField(gd, null, "outputChoices");
		
		gd.addChoice("Seed",seedChoices,seedChoices[0]);
		seedChoicesCF = gda.getChoiceField(gd, null, "seedChoices");
		
		gd.addCheckbox("Use Pixel Sizes", false);
		usePixelSizeCBF = gda.getCheckboxField(gd, "usePixelSize");

		gd.addMessage("Notes:\nIn-place calculation converts the binary image to 32-Bit."
				+ "\nMapping \"Point(s)\" tortuosity takes 2x longer, be patient."
				+ "\nThe un-mapped component voxel values are set to -2"
				+ "\nUnreachable mapped voxel values are set to -1.",myFont,Color.BLACK);
		gd.addHelp("https://lazzyizzi.github.io/Geodesic.html");
		gd.setBackground(myColor);
		gd.showDialog();

		if(gd.wasCanceled()) return null;

		dp.floodChoice = gd.getNextRadioButton();
		dp.taskChoice = gd.getNextRadioButton();
		dp.destChoice = gd.getNextChoice();
		dp.seedChoice = gd.getNextChoice();
		dp.useSize = gd.getNextBoolean();

		return dp;
	}

	//*********************************************************************************************

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		if(e!=null)
		{
			Object src = e.getSource();
			if(src instanceof Choice)
			{
				Choice choice = (Choice)src;
				String name = choice.getName();
				switch(name)
				{
				case "outputChoices":
					String destChoice = outputChoicesCF.getChoice().getSelectedItem();
					Choice seedChoices = seedChoicesCF.getChoice();
					int oldSeedChoice = seedChoices.getSelectedIndex();

					switch(destChoice)
					{
					case "2D this slice new image":
					case "2D new Image":
					case "2D in Place":
						seedChoices.removeAll();
						//Use the Addin method to load the choices
						seedChoicesCF.setChoices(seedChoices2D);
						switch(oldSeedChoice)
						{
						case 0: case 1: case 2: case 3://"LeftSlice","RightSlice","TopSlice","BottomSlice"
							seedChoices.select(oldSeedChoice);
							break;
						case 4://"FrontSlice"
							seedChoices.select(0);//becomes "LeftEdge"
							break;
						case 5://"BackSlice"
							seedChoices.select(1);//becomes "RightEdge"
							break;
						case 6://"Point(s)"
							seedChoices.select(4);//becomes "Point(s)"
							break;
						}
						break;
					case "3D new Image": case "3D in Place":
						seedChoices.removeAll();
						seedChoicesCF.setChoices(seedChoices3D);
						switch(oldSeedChoice)
						{
						case 0: case 1: case 2: case 3://"LeftEdge","RightEdge","TopEdge","BottomEdge","Point(s)"
							seedChoices.select(oldSeedChoice);
							break;
						case 4://"Point(s)"
							seedChoices.select(6);//becomes "Point(s)"
							break;
						}
						break;						
					}
				}
			}
		}
		return true;
	}

	//*********************************************************************************************

	private boolean ValidateParams(ImagePlus theImp, DialogParams dp)
	{
		if(dp.seedChoice == "Point(s)")
		{
			Roi roi = theImp.getRoi();
			if(roi==null || roi.getType() != Roi.POINT)
			{
				IJ.showMessage("Point ROI(s) required", "Please use the point tool to select \n"
						+ "origin point(s) in the material to be mapped");
				return false;
			}
		}
		return true;
	}
	
	//********************************************************************************

	private SlicePoints[] GetSlicePoints(ImagePlus theImp)
	{
		int i,j,k;
		SlicePoints[] slicePoints	= null;

		Roi roi= theImp.getRoi();

		if(roi.getType()==Roi.POINT)
		{
			PointRoi pRoi = (PointRoi) roi;
			Polygon p = roi.getPolygon();
			int[] colPts = new int[p.npoints];
			int[] rowPts = new int[p.npoints];
			int[] sliceNum = new int[p.npoints];

			for(i = 0; i<p.npoints;i++)
			{
				colPts[i] = p.xpoints[i];
				rowPts[i] = p.ypoints[i];
				sliceNum[i] = pRoi.getPointPosition(i);				
			}

			//1.count the number of slices with points in them
			int sliceCnt=1;
			for(i=1;i< sliceNum.length;i++)
			{
				if(sliceNum[i] != sliceNum[i-1])
				{
					sliceCnt++;
				}
			}		
			//IJ.log("sliceCnt=" + sliceCnt);

			//2.create a SlicePoints object to hold the data
			slicePoints = new SlicePoints[sliceCnt];
			for(i=0;i<sliceCnt;i++) slicePoints[i] = new SlicePoints();

			//3. loop through again an add the slice number
			j=0;
			slicePoints[j].sliceNum = sliceNum[j];
			j++;
			for(i=1;i< sliceNum.length;i++)
			{
				if(sliceNum[i] != sliceNum[i-1])
				{
					slicePoints[j].sliceNum = sliceNum[i];
					j++;
				}
			}

			//4. loop through again to get the point count in each slice
			int[] pointCnt = new int[sliceCnt];
			for(j=0;j<sliceCnt;j++)
			{
				for(i=0;i<sliceNum.length;i++)
				{
					if(sliceNum[i] == slicePoints[j].sliceNum) pointCnt[j]++;
				}
			}

			//5.Allocate the row and col arrays
			for(j=0;j<sliceCnt;j++)
			{
				slicePoints[j].iCol = new int[pointCnt[j]];
				slicePoints[j].jRow = new int[pointCnt[j]];
			}

			//6. loop through again to populate the row and col arrays
			for(j=0;j<sliceCnt;j++)
			{
				k=0;
				for(i=0;i<sliceNum.length;i++)
				{
					if(sliceNum[i] == slicePoints[j].sliceNum)
					{
						slicePoints[j].iCol[k]=colPts[i];
						slicePoints[j].jRow[k]=rowPts[i];
						k++;
					}
				}
			}
		}
		return slicePoints;
	}

	//*********************************************************************************************

}
