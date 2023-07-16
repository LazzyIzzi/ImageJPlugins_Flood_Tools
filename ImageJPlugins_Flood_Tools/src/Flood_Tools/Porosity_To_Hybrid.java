package Flood_Tools;

import java.awt.Color;
import java.awt.Font;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import jhd.FloodFill.ExactEuclideanMap;

public class Porosity_To_Hybrid implements PlugInFilter {

	class PhiImageStats
	{
		//voxel counts
		int solidVoxelCount;
		int resolvedPoreVoxelCount;
		int unresolvedPoreVoxelCount;

		double resolvedSolidVolume;
		double resolvedPoreVolume;
		double unresolvedPoreVolume;
		double unresolvedSolidVolume;
	}
	
	class HybridImageStats
	{
		float resolvedPoreMaxRadius;
		float resolvedPoreMinRadius;
		float unresolvedMaxPorosity;
		float unresolvedMinPorosity;		
	}
	
	ImagePlus imp;
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff
	
	//*******************************************************************************************
	
	@Override
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_32+STACK_REQUIRED+NO_UNDO;
	}

	//*******************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		imp = IJ.getImage();
		ImageStack stack = imp.getStack();
		Object[] oImageArr = stack.getImageArray();
		
		int w = stack.getWidth();
		int h = stack.getHeight();
		int d = stack.getSize();

		Calibration cal = imp.getCalibration();
		double pw = (float)cal.pixelWidth;
		double ph = (float)cal.pixelHeight;
		double pd = (float)cal.pixelDepth;
		if(pw<1 || ph<1 || pd<1)
		{
			IJ.error("Voxel width(X), height(Y), and depth(Z) must all be greater than 1 unit\n"
					+ "and have the same units."
					+ "Example: Change 0.03cm x 0.02cm x 2.5mm to 3 x 2 x 2.5mm ");
			return;
		}
		
		String ux = cal.getXUnit();
		String uy = cal.getYUnit();
		String uz = cal.getZUnit();
		if(!uy.equals(ux) || !uz.equals(ux))
		{
			IJ.error("Voxel X, Y, and Z units must be the same");
			return;
		}
		
		GenericDialog gd = new GenericDialog("Porosity To HybridPorosity");
		gd.addMessage("Converts porosity image\n"
				+ "resolved pores x = 1\n"
				+ "unresolved pores 0<x<1\n"
				+ "resolved solid x=0\n"
				+ "to hybrid porosity image\n"
				+ "resolved pores x = Euclidean distance\n"
				+ "unresolved pores 0<x<1\n"
				+ "resolved solid x=0\n",myFont);
		gd.addCheckbox("Create new Image", true);
		gd.addCheckbox("Show Statistics", true);
		gd.setBackground(myColor);
		gd.addHelp("https://lazzyizzi.github.io/DistanceMapPages/PorosityToHybrid.html");
		gd.showDialog();
		
		if(gd.wasCanceled()) return;
		
		boolean createNewImage = gd.getNextBoolean();
		boolean showStats = gd.getNextBoolean();
		
		if(createNewImage)
		{
			String title = imp.getTitle();
			if(title.endsWith(".tif"))
			{
				title = title.replace(".tif", "_Hybrid.tif");
			}
			else
			{
				title = title + "_Hybrid";
			}
			imp = imp.duplicate();
			imp.setTitle(title);
			imp.show();
			oImageArr = imp.getStack().getImageArray();			
		}
		
		PhiImageStats phiStats = getPhiImageStats(oImageArr,w,h,d,pw,ph,pd);
		
		phiMapToHybridMap(oImageArr,w,h,d,pw,ph,pd);
		
		HybridImageStats hybridStats = getHybridImageStats(oImageArr,w,h,d,pw,ph,pd);
				
		if(showStats)showAllStats(phiStats,hybridStats);
		setProperties(phiStats,hybridStats);
		IJ.run("Fire");
		IJ.run("Enhance Contrast", "saturated=0.35");		
	}
	
	//*******************************************************************************************

	private void setProperties(PhiImageStats phiStats, HybridImageStats hybridStats)
	{

		//String unit = imp.getCalibration().getXUnit();

		imp.setProp("MapType", "HybridPorosity");
		
		imp.setProp("resPoreVoxCnt",IJ.d2s(phiStats.resolvedPoreVoxelCount, 0));
		imp.setProp("unresPoreVoxCnt",IJ.d2s(phiStats.unresolvedPoreVoxelCount, 0));
		imp.setProp("resSolidVoxCnt",IJ.d2s(phiStats.solidVoxelCount, 0));
		
		imp.setProp("resPoreVol",IJ.d2s(phiStats.resolvedPoreVolume,2));
		imp.setProp("unresPoreVol",IJ.d2s(phiStats.unresolvedPoreVolume,2));
		imp.setProp("resSolidVol",IJ.d2s(phiStats.resolvedSolidVolume,2));
		imp.setProp("unresSolidVol",IJ.d2s(phiStats.unresolvedSolidVolume,2));
		
		double totVol = phiStats.resolvedPoreVolume + phiStats.resolvedSolidVolume +
				phiStats.unresolvedSolidVolume + phiStats.unresolvedPoreVolume;
		
		imp.setProp("resPhi",IJ.d2s(phiStats.resolvedPoreVolume/totVol,4));
		imp.setProp("unresPhi",IJ.d2s(phiStats.unresolvedPoreVolume/totVol,4));
		double totPhi =(phiStats.resolvedPoreVolume + phiStats.unresolvedPoreVolume)/totVol;	
		imp.setProp("totPhi",IJ.d2s(totPhi,4));
		
		imp.setProp("resSolidVolFrac",IJ.d2s(phiStats.resolvedSolidVolume/totVol,4));
		imp.setProp("unresSolidVolFrac",IJ.d2s(phiStats.unresolvedSolidVolume/totVol,4));
		
		imp.setProp("resPoreMaxR",IJ.d2s(hybridStats.resolvedPoreMaxRadius,4));
		imp.setProp("resPoreMinR",IJ.d2s(hybridStats.resolvedPoreMinRadius,4));
		imp.setProp("unresMaxPhi",IJ.d2s(hybridStats.unresolvedMaxPorosity,4));
		imp.setProp("unresMinPhi",IJ.d2s(hybridStats.unresolvedMinPorosity,4));			
	}
	
	//*******************************************************************************************

	private void showAllStats(PhiImageStats phiStats, HybridImageStats hybridStats)
	{
		String unit = imp.getCalibration().getXUnit();

		ResultsTable rt = ResultsTable.getResultsTable("Porosity To Hybrid Results");
		if(rt == null) rt = new ResultsTable();
		rt.setPrecision(3);
		rt.incrementCounter();
		rt.setLabel(imp.getTitle(), rt.getCounter()-1);
		rt.addValue("resPoreVoxCnt",IJ.d2s(phiStats.resolvedPoreVoxelCount,0));
		rt.addValue("unresPoreVoxCnt",IJ.d2s(phiStats.unresolvedPoreVoxelCount,0));
		rt.addValue("resSolidVoxCnt",IJ.d2s(phiStats.solidVoxelCount,0));
		int totCnt = phiStats.resolvedPoreVoxelCount+phiStats.unresolvedPoreVoxelCount+phiStats.solidVoxelCount;
		rt.addValue("totalVoxCnt",IJ.d2s(totCnt,0));
		
		rt.addValue("resPoreVol "+unit+(char)0x0b3,IJ.d2s(phiStats.resolvedPoreVolume,2));
		rt.addValue("unresPoreVol "+unit+(char)0x0b3,IJ.d2s(phiStats.unresolvedPoreVolume,2));
		rt.addValue("resSolidVol "+unit+(char)0x0b3,IJ.d2s(phiStats.resolvedSolidVolume,2));
		rt.addValue("unresSolidVol "+unit+(char)0x0b3,IJ.d2s(phiStats.unresolvedSolidVolume,2));
		
		double totVol = phiStats.resolvedPoreVolume + phiStats.resolvedSolidVolume +
				phiStats.unresolvedSolidVolume + phiStats.unresolvedPoreVolume;
		
		rt.addValue("resPhi",IJ.d2s(phiStats.resolvedPoreVolume/totVol,4));
		rt.addValue("unresPhi",IJ.d2s(phiStats.unresolvedPoreVolume/totVol,4));
		double totPhi = (phiStats.resolvedPoreVolume + phiStats.unresolvedPoreVolume)/totVol;	
		rt.addValue("totPhi",IJ.d2s(totPhi,4));
		
		rt.addValue("resSolidVolFrac",IJ.d2s(phiStats.resolvedSolidVolume/totVol,4));
		rt.addValue("unresSolidVolFrac",IJ.d2s(phiStats.unresolvedSolidVolume/totVol,4));
		
		rt.addValue("resPoreMaxR "+unit,IJ.d2s(hybridStats.resolvedPoreMaxRadius,4));
		rt.addValue("resPoreMinR "+unit,IJ.d2s(hybridStats.resolvedPoreMinRadius,4));
		rt.addValue("unresMaxPhi "+unit,IJ.d2s(hybridStats.unresolvedMaxPorosity,4));
		rt.addValue("unresMinPhi "+unit,IJ.d2s(hybridStats.unresolvedMinPorosity,4));
		
		rt.show("Porosity To Hybrid Results");
	}
	//*******************************************************************************************

	private PhiImageStats getPhiImageStats(Object[] oImageArr, int width, int height, int depth,
	double 	pixWidth, double pixHeight, double pixDepth)
	{
		PhiImageStats stats = new PhiImageStats();

		//initialize counters
		stats.resolvedPoreVoxelCount = 0;
		stats.unresolvedPoreVoxelCount = 0;
		stats.solidVoxelCount=0;
		stats.unresolvedPoreVolume=0;
		stats.unresolvedSolidVolume=0;
		
		double voxVal;
		double voxelVolume = (float)(pixWidth*pixHeight*pixDepth);
		
		for(int k=0;k<depth;k++)
		{
			float[] slice = (float[])oImageArr[k];
			for(int i=0;i<width;i++)
			{
				for(int j=0;j<height;j++)
				{
					voxVal = slice[i+j*width];
					if(voxVal <1.0 && voxVal>0)
					{
						stats.unresolvedPoreVolume += voxVal*voxelVolume;
						stats.unresolvedSolidVolume += (1-voxVal)*voxelVolume;
						stats.unresolvedPoreVoxelCount++;
					}
					else if(voxVal==0)
					{
						stats.solidVoxelCount++;
					}
					else
					{
						stats.resolvedPoreVoxelCount++;
					}
				}
			}
		}
		
		stats.resolvedPoreVolume = stats.resolvedPoreVoxelCount*voxelVolume;
		stats.resolvedSolidVolume = stats.solidVoxelCount*voxelVolume;
		return stats;
	}

	//*******************************************************************************************

	private HybridImageStats getHybridImageStats(Object[] oImageArr, int width, int height, int depth,
	double 	pixWidth, double pixHeight, double pixDepth)
	{
		HybridImageStats stats = new HybridImageStats();
		stats.resolvedPoreMaxRadius = Float.MIN_VALUE;
		stats.resolvedPoreMinRadius = Float.MAX_VALUE;
		stats.unresolvedMaxPorosity = Float.MIN_VALUE;
		stats.unresolvedMinPorosity = Float.MAX_VALUE;
		float voxVal;
		int unresPhiCnt=0;

		for(int k=0;k<depth;k++)
		{
			float[] slice = (float[])oImageArr[k];
			for(int i=0;i<width;i++)
			{
				for(int j=0;j<height;j++)
				{
					voxVal = slice[i+j*width];
					if(voxVal>=1)
					{
						if(voxVal>stats.resolvedPoreMaxRadius)
						{
							stats.resolvedPoreMaxRadius=voxVal;
						}
						else if(voxVal<stats.resolvedPoreMinRadius)
						{
							stats.resolvedPoreMinRadius=voxVal;
						}
					}
					else if(voxVal>0 && voxVal < 1)
					{
						if(voxVal>stats.unresolvedMaxPorosity)
						{
							stats.unresolvedMaxPorosity=voxVal;
						}
						else if(voxVal<stats.unresolvedMinPorosity)
						{
							stats.unresolvedMinPorosity=voxVal;
						}
						unresPhiCnt++;
					}
				}
			}
		}
		if(unresPhiCnt==0)
		{
			stats.unresolvedMaxPorosity=0;
			stats.unresolvedMinPorosity=0;
		}
		return stats;
	}
	
	//*******************************************************************************************

	public void phiMapToHybridMap(Object[] oImageArr, int width, int height, int depth,
			double 	pixWidth, double pixHeight, double pixDepth)
	{
		
		//Input porosity maps consist of three domains
		//1. unresolved porosity 0<x<1
		//2. resolved porosity x>=1
		//3. solid x=0
		
		//Hybrid maps also consist of three domains
		//1. unresolved porosity 0<x<1
		//2. resolved porosity a Euclidean map of resolved porosity x>=1
		//3. solid x=0
		
		//Create a temporary copy of the image data
		Object[] oImgCopy = new Object[depth];
		for(int k=0;k<depth;k++)
		{
			oImgCopy[k]= new float[width*height];
			float[] imgSlice = (float[])oImageArr[k];
			float[] copySlice = (float[])oImgCopy[k];
			for(int i=0; i<width*height;i++)
			{
				copySlice[i] = imgSlice[i];
				if(imgSlice[i] < 1) imgSlice[i]=0;
			}
		}		
		
		//Compute the edm non-zero domain of the modified original image
		ExactEuclideanMap edm = new ExactEuclideanMap();
		edm.edm3D(oImageArr, width, height, depth, (float)pixWidth, (float)pixHeight, (float)pixDepth, "Map !0", true);
		
		//Copy the unresolved porosity from the copy into the original image
//		double floodMax=Double.MIN_VALUE;
//		double floodMin=Double.MAX_VALUE;
		for(int k=0;k<depth;k++)
		{
			float[] imgSlice = (float[])oImageArr[k];
			float[] copySlice = (float[])oImgCopy[k];
			for(int i=0; i<width*height;i++)
			{
				if(copySlice[i] < 1) imgSlice[i]=copySlice[i];
//				if(floodMax < imgSlice[i]) floodMax = imgSlice[i];
//				if(floodMin > imgSlice[i] && imgSlice[i]>0) floodMin = imgSlice[i];
			}
		}
		
//		return floodMax;
	}

}
