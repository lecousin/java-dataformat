package net.lecousin.dataformat.image.test;

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.BoxLayout;

public class ImageViewer extends Frame {

	public static interface ImagesProvider {
		public BufferedImage[] nextImages(StringBuffer name);
	}
	
	public ImageViewer(String[] titles, ImagesProvider provider) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		imageName = new Label("");
		add(imageName);
		Panel panel = new Panel();
		panel.setLayout(new GridLayout(2, titles.length));
		for (int i = 0; i < titles.length; ++i)
			panel.add(new Label(titles[i]));
		images = new DisplayImage[titles.length];
		for (int i = 0; i < images.length; ++i)
			panel.add(images[i] = new DisplayImage());
		add(panel);
		Button button = new Button("Next");
		add(button);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				StringBuffer name = new StringBuffer();
				BufferedImage[] img = provider.nextImages(name);
				if (img == null)
					System.exit(0);
				imageName.setText(name.toString());
				for (int i = 0; i < img.length; ++i)
					images[i].img = img[i];
				doLayout();
				repaint();
				for (int i = 0; i < img.length; ++i)
					images[i].repaint();
			}
		});
	}
	
	private Label imageName;
	private DisplayImage[] images;
	
	private static class DisplayImage extends Canvas {
		public DisplayImage() {
		}
		private BufferedImage img = null;
		@Override
		public void paint(Graphics g) {
			if (img != null)
				g.drawImage(img, 0, 0, null);
		}
	}
	
}
