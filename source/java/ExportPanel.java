package org.covid;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import javax.swing.border.*;
import org.apache.log4j.*;
import org.rsna.ui.ColorPane;
import org.rsna.ui.RowLayout;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.IPUtil;
import org.rsna.util.StringUtil;


public class ExportPanel extends BasePanel implements ActionListener, KeyListener {

	static final Logger logger = Logger.getLogger(ExportPanel.class);

	Configuration config;
	JScrollPane jsp;
	JButton export;
	JButton refresh;
	JButton clear;
	PanelField repositoryIP;
	PanelField repositoryPort;
	JCheckBox enableExport;
	JPanel centerPanel;

	Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 12 );
	Font titleFont = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 18 );
	Font columnHeadingFont = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 14 );

	static ExportPanel exportPanel = null;
	
	ExecutorService exportExecutor = Executors.newSingleThreadExecutor();
	static final String hiddenExportFilename = "..export";

	public static synchronized ExportPanel getInstance() {
		if (exportPanel == null) exportPanel = new ExportPanel();
		return exportPanel;
	}

	protected ExportPanel() {
		super();
		config = Configuration.getInstance();
	
		//UI Components
		export = new JButton("Export");
		export.addActionListener(this);
		refresh = new JButton("Refresh");
		refresh.addActionListener(this);
		clear = new JButton("Clear All");
		clear.addActionListener(this);
		
		String ipString = config.getProps().getProperty("repositoryIP","");
		repositoryIP = new PanelField(ipString, 250);
		repositoryIP.addKeyListener(this);
		String portString = config.getProps().getProperty("repositoryPort", "8080");
		repositoryPort = new PanelField(portString);
		repositoryPort.addKeyListener(this);
		enableExport = new JCheckBox("Enable export");
		enableExport.setBackground(config.background);
		enableExport.addActionListener(this);
		enableExport.setSelected(config.getProps().getProperty("enableExport", "yes").equals("yes"));
		
		//Header
		Box header = Box.createHorizontalBox();
		header.setBackground(config.background);
		header.add(new JLabel(" Repository URL:  "));
		header.add(repositoryIP);
		header.add(new JLabel(" : "));
		header.add(repositoryPort);
		header.add(Box.createHorizontalGlue());
		header.add(enableExport);
		add(header, BorderLayout.NORTH);

		//Main panel
		BasePanel mainPanel = new BasePanel();
		Border inner = BorderFactory.createEmptyBorder(2, 0, 0, 0);
		Border outer = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		mainPanel.setBorder(BorderFactory.createCompoundBorder(outer, inner));
		//Put in the title
		JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 10));
		titlePanel.setBackground(config.background);
		JLabel panelTitle = new JLabel("Export Metadata and Studies");
		panelTitle.setFont(titleFont);
		panelTitle.setForeground(Color.BLUE);
		titlePanel.add(panelTitle);
		mainPanel.add(titlePanel, BorderLayout.NORTH);
		//Put a scroll pane in the center panel
		JScrollPane jsp = new JScrollPane();
		jsp.setBackground(config.background);
		jsp.getViewport().setBackground(config.background);
		mainPanel.add(jsp, BorderLayout.CENTER);
		//Make a panel to hold the table datasets
		JPanel cp = new JPanel(new FlowLayout(FlowLayout.CENTER));
		cp.setBackground(config.background);
		centerPanel = new BasePanel();
		centerPanel.setLayout(new RowLayout(20, 5));
		cp.add(centerPanel);
		jsp.setViewportView(cp);
		//Now put the main panel in the center of the parent layout
		add(mainPanel, BorderLayout.CENTER);
		
		listCases();
		
		//Footer
		Box footer = Box.createHorizontalBox();
		footer.setBackground(config.background);
		footer.add(clear);
		footer.add(Box.createHorizontalStrut(10));
		footer.add(refresh);
		footer.add(Box.createHorizontalGlue());
		footer.add(export);
		add(footer, BorderLayout.SOUTH);
	}
	
	private void listCases() {
		centerPanel.removeAll();
		//Put in a vertical margin
		centerPanel.add(Box.createVerticalStrut(10));
		centerPanel.add(RowLayout.crlf());
		//Put in the column headings
		centerPanel.add(Box.createHorizontalStrut(5)); //no heading for the checkboxes
		centerPanel.add(new HeadingLabel("PatientID"));
		centerPanel.add(new HeadingLabel("Metadata"));
		centerPanel.add(new HeadingLabel("Export"));
		centerPanel.add(RowLayout.crlf());
		//Put in the cases
		File[] cases = config.getStorageDir().listFiles();
		for (File caseDir : cases) {
			if (caseDir.isDirectory()) {
				String metadataDate = "";
				File metadata = new File(caseDir, "metadata.xml");
				if (metadata.exists()) {
					metadataDate = StringUtil.getDate(metadata.lastModified(), ".");
				}
				String exportDate = "";
				File export = new File(caseDir, hiddenExportFilename);
				if (export.exists()) {
					exportDate = StringUtil.getDate(export.lastModified(), ".");
				}
				CaseLabel cl = new CaseLabel(exportDate);
				CaseCheckBox cb = new CaseCheckBox(caseDir, cl);
				centerPanel.add(cb);
				centerPanel.add(new CaseLabel(caseDir.getName()));
				centerPanel.add(new CaseLabel(metadataDate));
				centerPanel.add(cl);
				centerPanel.add(RowLayout.crlf());
			}
		}		
	}
	
	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(export)) {
			LinkedList<File> cases = new LinkedList<File>();
			Component[] comps = centerPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof CaseCheckBox) {
					CaseCheckBox ccb = (CaseCheckBox)c;
					if (ccb.isSelected()) cases.add(ccb.file);
				}
			}
			startExport(cases);
		}
		else if (source.equals(clear)) {
			Component[] comps = centerPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof CaseCheckBox) {
					((CaseCheckBox)c).setSelected(false);
				}
			}
		}
		else if (source.equals(refresh)) {
			listCases();
			centerPanel.revalidate();
			centerPanel.repaint();
		}
		else if (source.equals(enableExport)) {
			boolean enb = enableExport.isSelected();
			config.getProps().setProperty("enableExport", (enb?"yes":"no"));
		}
	}
	
	public void keyTyped(KeyEvent event) { }
	public void keyPressed(KeyEvent event) { }
	public void keyReleased(KeyEvent event) {
		config.getProps().setProperty("repositoryPort", repositoryPort.getText().trim());
		config.getProps().setProperty("repositoryIP", repositoryIP.getText().trim());
	}
	
	class PanelField extends JTextField {
		public PanelField(String text) {
			this(text, 40);
		}
		public PanelField(String text, int width) {
			super(text);
			setFont(mono);
			Dimension d = getPreferredSize();
			d.width = width;
			setMaximumSize(d);
			setMinimumSize(d);
			setPreferredSize(d);
		}
	}
	
	class HeadingLabel extends JLabel {
		public HeadingLabel(String text) {
			this(text, 0.0f);
		}
		public HeadingLabel(String text, float alignmentX) {
			super(text);
			setFont(columnHeadingFont);
			setForeground(Color.BLUE);
			setAlignmentX(alignmentX);
		}
	}
	
	class CaseCheckBox extends JCheckBox {
		File file;
		CaseLabel label;
		public CaseCheckBox(File file, CaseLabel label) {
			super();
			setBackground(config.background);
			this.file = file;
			this.label = label;
		}
	}
	
	class CaseLabel extends JLabel {
		public CaseLabel(String text) {
			this(text, 0.0f);
		}
		public CaseLabel(String text, float alignmentX) {
			super(text);
			setFont( mono );
			setAlignmentX(alignmentX);
		}
	}

	private void startExport(LinkedList<File> cases) {
		try {
			for (File caseDir : cases) {
				URL url = new URL(repositoryIP.getText().trim() + ":" + repositoryPort.getText().trim());
				exportExecutor.execute(new ExportThread(caseDir, url, enableExport.isSelected()));
			}
		}
		catch (Exception ex) {
			logger.warn("Export failed", ex);
		}
	}
	
	class ExportThread extends Thread {
		final long maxUnchunked = 20;
		final int oneSecond = 1000;
		final int connectionTimeout = 20 * oneSecond;
		final int readTimeout = 120 * oneSecond;
		File dir;
		URL url;
		boolean enableExport;
		public ExportThread(File dir, URL url, boolean enableExport) {
			super();
			this.dir = dir;
			this.url = url;
			this.enableExport = enableExport;
		}
		public void run() {
			File manifestFile = createManifest(dir);
			File expFile = new File(dir, hiddenExportFilename);
			if (expFile.exists()) expFile.delete();
			File zipDir = new File("zip");
			zipDir.mkdirs();
			File parent = dir.getParentFile();
			File zip = new File(zipDir, dir.getName()+".zip");
			if (FileUtil.zipDirectory(manifestFile, dir, zip)) {
				if (enableExport) {
					if (export(zip)) {
						FileUtil.setText(expFile, StringUtil.getDateTime(" "));
						updateTable(dir);
						zip.delete();
					}
				}
			}
			else logger.warn("Unable to create zip file for export ("+zip+")");
			//zip.delete(); //commented out for testing
		}
		private File createManifest(File dir) {
			String uid = Long.toString(System.currentTimeMillis());
			String patientID = dir.getName();
			StringBuffer sb = new StringBuffer();
			sb.append("<manifest\n");
			sb.append("  uid=\""+uid+"\"\n");
			sb.append("  patientID=\""+patientID+"\"\n");
			sb.append("  date=\""+StringUtil.getDate(".")+"\"\n");
			sb.append("/>\n");
			File manifest = new File(dir, "manifest.xml");
			FileUtil.setText(manifest, sb.toString());
			return manifest;
		}
		private boolean export(File fileToExport) {
			//Do not export zero-length files
			long fileLength = fileToExport.length();
			if (fileLength == 0) return false;

			HttpURLConnection conn = null;
			OutputStream svros = null;
			try {
				//Establish the connection
				conn = HttpUtil.getConnection(url);
				conn.setReadTimeout(connectionTimeout);
				conn.setConnectTimeout(readTimeout);
				logger.debug("Export: file: "+fileToExport.getName()+" [len="+fileLength+"]");
				if (fileLength > maxUnchunked) conn.setChunkedStreamingMode(0);
				conn.connect();

				//Send the file to the server
				svros = conn.getOutputStream();
				FileUtil.streamFile(fileToExport, svros);

				//Get the response code and log Unauthorized responses
				int responseCode = conn.getResponseCode();
				logger.debug("Export: transmission response code = "+responseCode);
				if (responseCode != 200) return false;

				//Get the response.
				//Note: this rather odd way of acquiring a success
				//result is for backward compatibility with MIRC.
				//We leave the input stream open in order to make
				//the disconnect actually close the connection.
				String result = "";
				try { result = FileUtil.getTextOrException( conn.getInputStream(), FileUtil.utf8, false ); }
				catch (Exception ex) { logger.warn("Unable to read response: "+ex.getMessage()); }
				logger.debug("Export: response: "+result);
				conn.disconnect();
				if (result.equals("OK")) return true;
			}
			catch (Exception e) {
				if (logger.isDebugEnabled()) logger.debug("Export: transmission failed: " + e.getMessage(), e);
				else logger.warn("Export: transmission failed: " + e.getMessage());
			}
			return false;
		}
	
		//TODO: figure out how to update the centerPanel without
		//destroying any work the user has done while the
		//background threads have been running.
		private void updateTable(File dir) {
			final File caseDir = dir;
			final JPanel panel = centerPanel;
			Runnable r = new Runnable() {
				public void run() {
					Component[] comps = centerPanel.getComponents();
					for (Component c : comps) {
						if (c instanceof CaseCheckBox) {
							CaseCheckBox cb = (CaseCheckBox)c;
							if (cb.file.equals(caseDir)) {
								File expFile = new File(caseDir, hiddenExportFilename);
								if (expFile.exists()) {
									String exportDate = StringUtil.getDate(expFile.lastModified(), ".");
									cb.label.setText(exportDate);
									return;
								}
							}
						}
					}
				}
			};
			SwingUtilities.invokeLater(r);
		}
	}
	
}
