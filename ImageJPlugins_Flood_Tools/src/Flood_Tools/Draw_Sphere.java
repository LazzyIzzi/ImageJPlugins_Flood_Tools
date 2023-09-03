package Flood_Tools;

import java.awt.*;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.filter.*;
import ij.process.ImageProcessor;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.*;
import jhd.FloodFill.EuclideanSpheres;
import jhd.FloodFill.EuclideanSpheres.SphereParams;

public class Draw_Sphere implements PlugInFilter, DialogListener 
{

	ImagePlus imp;
	NumericField xPixNF,yPixNF,zPixNF,rUnitsNF,fillValNF;
	MessageField xUnitsMF,yUnitsMF,zUnitsMF,rPixMF;
	Calibration cal;
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
		double r = 10;

		this.imp = IJ.getImage();
		int x =imp.getWidth()/2;
		int y = imp.getHeight()/2;
		int z = imp.getImageStackSize()/2;
		cal = imp.getCalibration();

		if(!(cal.getXUnits().equals(cal.getYUnits()) && cal.getXUnits().equals(cal.getZUnits())))
		{
			IJ.error("X, Y, and Z units must be the same.\n"
					+ "X, Y, and Z dimensions may be different.");
			return;
		}
		GenericDialog gd = new GenericDialog("Draw Sphere");
		GenericDialogAddin gda = new GenericDialogAddin();
		
		gd.addMessage("Draws a \"sphere\" of radius R centered at a voxel (X,Y,Z) location.\n"
				+ "X,Y,Z units must be the same.\n"
				+ "X,Y,Z dimensions may be different.\n"
				+ "X,Y,Z locations outside of the image are allowed.\n"
				+ "Sphere voxels outside of the image will be clipped.\n"
				+ "Negative radii are not allowed.\n"
				+ "Sub-voxel resolution is not supported.", myFont);

		gd.addNumericField("x coordinate in pixels", x, 3);
		xPixNF = gda.getNumericField(gd, null, "xPix");
		gd.addToSameRow();
		gd.addMessage("="+(x*cal.pixelWidth)+cal.getXUnit());
		xUnitsMF = gda.getMessageField(gd, "xUnits");

		gd.addNumericField("y coordinate in pixels", y, 3);
		yPixNF = gda.getNumericField(gd, null, "yPix");
		gd.addToSameRow();
		gd.addMessage("="+(y*cal.pixelHeight)+cal.getYUnit());
		yUnitsMF = gda.getMessageField(gd, "yUnits");

		gd.addNumericField("z coordinate in pixels", z, 3);
		zPixNF = gda.getNumericField(gd, null, "zPix");
		gd.addToSameRow();
		gd.addMessage("="+(z*cal.pixelDepth)+cal.getZUnit());
		zUnitsMF = gda.getMessageField(gd, "zUnits");

		gd.addNumericField("Radius in image units", r, 3);
		rUnitsNF = gda.getNumericField(gd, null, "rUnits");
		gd.addToSameRow();
		gd.addMessage("Rx="+IJ.d2s(r/cal.pixelWidth, 2)+",Ry="+IJ.d2s(r/cal.pixelHeight,2)+",Rz="+IJ.d2s(r/cal.pixelDepth,2)+"pixels");
		rPixMF = gda.getMessageField(gd, "rPix");

		gd.addNumericField("Fill Sphere with value", 255, 3);
		fillValNF = gda.getNumericField(gd, null, "fillVal");
		
		gd.addHelp("https://lazzyizzi.github.io/DistanceMapPages/EuclideanSpheres.html");
		gd.addDialogListener(this);
		gd.setBackground(myColor);
		gd.showDialog();

		if(gd.wasCanceled()) return;

		Calibration cal = imp.getCalibration();
		EuclideanSpheres myES = new EuclideanSpheres();
		SphereParams sp =  new  SphereParams();

		sp.centerX= (int)gd.getNextNumber();
		sp.centerY= (int)gd.getNextNumber();
		sp.centerZ= (int)gd.getNextNumber();
		sp.radius = gd.getNextNumber();
		sp.fillVal = gd.getNextNumber();				
		sp.pixWidth = cal.pixelWidth;
		sp.pixHeight = cal.pixelHeight;
		sp.pixDepth = cal.pixelDepth;
		sp.unit = cal.getUnit();

		ImageStack stack = imp.getStack();
		int imgWidth = stack.getWidth();
		int imgHeight = stack.getHeight();
		int imgDepth = stack.size();
		Object[] data = stack.getImageArray();

		int pixcount = myES.DrawSphere(data, imgWidth, imgHeight, imgDepth,sp);
		imp.updateAndDraw();
		IJ.showStatus("Voxels filled="+pixcount);		
	}
	
	//******************************************************************************************************

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		boolean dialogOK = true;
		
		if(e!=null)
		{
			Object src = e.getSource();
			if(src instanceof TextField)
			{
				TextField tf = (TextField)src;
				String name = tf.getName();
				double xPix,yPix,zPix;
				switch(name)
				{
				case "xPix":
					xPix = xPixNF.getNumber();
					xUnitsMF.setLabel("="+(xPix*cal.pixelWidth)+cal.getXUnit());					
					break;
				case "yPix":
					yPix = yPixNF.getNumber();
					yUnitsMF.setLabel("="+(yPix*cal.pixelHeight)+cal.getYUnit());					
					break;
				case "zPix":
					zPix = zPixNF.getNumber();
					zUnitsMF.setLabel("="+(zPix*cal.pixelDepth)+cal.getZUnit());					
					break;
				case "rUnits":
					double r = rUnitsNF.getNumber();
					//negative radii are not allowed
					if(r<=0)
					{
						rUnitsNF.getNumericField().setBackground(Color.red);
						rPixMF.setLabel("");
						dialogOK=false;
					}
					else
					{
						rUnitsNF.getNumericField().setBackground(Color.white);
						rPixMF.setLabel("Rx="+IJ.d2s(r/cal.pixelWidth, 2)+",Ry="+IJ.d2s(r/cal.pixelHeight,2)+",Rz="+IJ.d2s(r/cal.pixelDepth,2)+"pixels");
						dialogOK = true;
					}
					break;
				case "fillVal":
					double fillVal = fillValNF.getNumber();
					fillValNF.getNumericField().setBackground(Color.white);
					dialogOK = true;
					if(Double.isNaN(fillVal))
					{
						fillValNF.getNumericField().setBackground(Color.red);
						dialogOK = false;
					}					
					else switch(imp.getBitDepth())
					{
					case 32:
						if(fillVal>Float.MAX_VALUE || fillVal < -Float.MAX_VALUE)
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
				gd.pack();
			}
				
		}
		return dialogOK;
	}	
}
