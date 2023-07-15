package Flood_Tools;

import java.awt.*;

import ij.*;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.*;
import ij.process.*;

//import jhd.DistanceMaps.libJ8.EuclideanSpheres;
import jhd.FloodFill.EuclideanSpheres;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.*;

public class Draw_Overlapping_Spheres implements PlugInFilter, DialogListener{

	ImagePlus imp;
	NumericField volFracNF,minRadiusNF,maxRadiusNF,fillValNF;
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff
	

	//******************************************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_8G+DOES_16+DOES_32+STACK_REQUIRED+NO_UNDO;		
	}

	//******************************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{		
		GenericDialog gd = new GenericDialog("Draw Spheres Random Points");
		GenericDialogAddin gda=new GenericDialogAddin();
		String[] unitChoices = {"Pixels","ImageUnits"};
		boolean[] unitChoiceValues = {false,true};
		gd.addNumericField("Volume Fraction of Spheres", .1, 3);
		volFracNF = gda.getNumericField(gd, null, "volFrac");
		gd.addNumericField("Min Radius in user units", 2, 3);
		gd.addNumericField("Max Radius in user units", 10, 3);
		gd.addNumericField("Fill Sphere with value", 255, 3);
		fillValNF = gda.getNumericField(gd, null, "fillVal");
		gd.addHelp("https://lazzyizzi.github.io/EuclideanSpheres.html");
		gd.addRadioButtonGroup("Units", unitChoices, 1, 2, unitChoices[0]);
		gd.addDialogListener(this);
		gd.setBackground(myColor);
		gd.showDialog();

		if(gd.wasCanceled()) return;
				
		EuclideanSpheres.RandomSphereParams rsp = new EuclideanSpheres.RandomSphereParams();
		EuclideanSpheres myES = new EuclideanSpheres();
		Calibration ce = imp.getCalibration();
		ImageStack stack = imp.getStack();
		
		// get user parameters from dialog
		rsp.volFrac		= gd.getNextNumber();
		rsp.minRadius	= gd.getNextNumber();
		rsp.maxRadius	= gd.getNextNumber();
		rsp.fillVal		= gd.getNextNumber();		
		rsp.pixWidth	= ce.pixelWidth;
		rsp.pixHeight	= ce.pixelHeight;
		rsp.pixDepth	= ce.pixelDepth;	
		int imgWidth	= stack.getWidth();
		int imgHeight	= stack.getHeight();
		int imgDepth	= stack.size();
		Object[] data	= stack.getImageArray();

		if(rsp.volFrac>=1.0)
		{
			IJ.showMessage("Volume fraction must be less than 1.0");
			return;
		}
		int pixcount = myES.DrawRandomSpheres(data,imgWidth,imgHeight,imgDepth, rsp);
		IJ.showStatus("Voxels filled="+pixcount);		
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
				switch(name)
				{
				case "volFrac":
					double val = volFracNF.getNumber();
					if(val<=0 || val>=1)
					{
						tf.setBackground(Color.red);
						dialogOK=false;
					}
					else if(val>0.90 && val<1)
					{
						tf.setBackground(Color.yellow);
						dialogOK=true;
					}
					else
					{
						tf.setBackground(Color.white);
						dialogOK=true;					
					}
				break;
				case "fillVal":
					double fillVal = fillValNF.getNumber();
					fillValNF.getNumericField().setBackground(Color.white);
					dialogOK = true;
					
					switch(imp.getBitDepth())
					{
					case 32:
						if(fillVal>Float.MAX_VALUE || fillVal<Float.MIN_VALUE)
						{
							fillValNF.getNumericField().setBackground(Color.red);
							dialogOK=false;								
						}
						break;
					case 16:
						if(fillVal>65535 || fillVal<0)
						{
							fillValNF.getNumericField().setBackground(Color.red);
							dialogOK=false;								
						}
						break;
					case 8:
						if(fillVal>255 || fillVal<0)
						{
							fillValNF.getNumericField().setBackground(Color.red);
							dialogOK=false;								
						}
						break;
					}
					break;
				}
			}
		}
		return dialogOK;
	}
}

