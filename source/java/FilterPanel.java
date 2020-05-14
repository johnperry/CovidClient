package org.covid;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import org.rsna.ui.ColorPane;
import org.rsna.util.FileUtil;

public class FilterPanel extends BasePanel implements ActionListener {

	public static ColorPane cp;

	JScrollPane jsp;
	JButton reset;
	JButton save;
	File file = new File("filter.script");
	JCheckBox filterSRs; //structured reports
	JCheckBox filterSCs; //secondary capture
	Configuration config;

	static FilterPanel filterPanel = null;

	public static synchronized FilterPanel getInstance() {
		if (filterPanel == null) filterPanel = new FilterPanel();
		return filterPanel;
	}

	protected FilterPanel() {
		super();
		config = Configuration.getInstance();

		cp = new ColorPane();
		cp.setScrollableTracksViewportWidth(false);
		cp.setEditable(true);

		BasePanel bp = new BasePanel();
		bp.add(cp, BorderLayout.CENTER);

		jsp = new JScrollPane();
		jsp.getVerticalScrollBar().setUnitIncrement(10);
		jsp.setViewportView(bp);
		jsp.getViewport().setBackground(Color.white);
		add(jsp, BorderLayout.CENTER);

		filterSRs = new JCheckBox("Remove Structured Report Files");
		String sel = config.getProps().getProperty("filterSRs", "yes");
		filterSRs.setSelected(!sel.equals("no"));
		filterSRs.setBackground(config.background);
		filterSRs.addActionListener(this);
		filterSCs = new JCheckBox("Remove Secondary Capture Files");
		sel = config.getProps().getProperty("filterSCs", "yes");
		filterSCs.setSelected(!sel.equals("no"));
		filterSCs.setBackground(config.background);
		filterSCs.addActionListener(this);
		reset = new JButton("Reset");
		reset.addActionListener(this);
		save = new JButton("Save");
		save.addActionListener(this);

		Box footer = Box.createHorizontalBox();
		footer.add(filterSRs);
		footer.add(Box.createHorizontalStrut(20));
		footer.add(filterSCs);
		footer.add(Box.createHorizontalGlue());
		footer.add(reset);
		footer.add(Box.createHorizontalStrut(3));
		footer.add(save);
		add(footer, BorderLayout.SOUTH);
		
		reload();
	}
	
	public String getText() {
		return cp.getText();
	}
	
	public void setFocus() {
		cp.requestFocus();
	}

	public void reload() {
		cp.clear();
		if (file.exists()) {
			try { cp.append( FileUtil.getText( file ) ); }
			catch (Exception ignore) { }
		}
	}
	
	public boolean getFilterSRs() {
		return filterSRs.isSelected();
	}

	public boolean getFilterSCs() {
		return filterSCs.isSelected();
	}

	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(reset)) reload();
		else if (source.equals(save)) {
			FileUtil.setText(file, cp.getText());
		}
		else if (source.equals(filterSRs)) {
			config.getProps().setProperty("filterSRs", (filterSRs.isSelected() ? "yes" : "no"));
		}
		else if (source.equals(filterSCs)) {
			config.getProps().setProperty("filterSCs", (filterSCs.isSelected() ? "yes" : "no"));
		}
	}

}