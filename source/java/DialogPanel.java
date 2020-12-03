package org.covid;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.nio.charset.Charset;
import org.rsna.ui.*;
import org.rsna.util.*;

public class DialogPanel extends JPanel {

	private static final Color headingColor = Color.BLUE;
	private static final Color paragraphColor = Color.BLACK;
	private static final Font headingFont = new Font( "SansSerif", Font.BOLD, 16 );
	private static final Font paragraphFont = new Font( "SansSerif", Font.PLAIN, 12 );
	private static final Font labelFont = new Font( "SansSerif", Font.BOLD, 12 );
	private static final Font fieldFont = new Font( "Monospaced", Font.PLAIN, 12 );
	private static final int fieldWidth = 30;
	private Hashtable<String,JComponent> fields;
	private String title = "";
	private boolean sessionMode = false;
	

	public DialogPanel() {
		super();
		setBorder(BorderFactory.createEmptyBorder(0,0,0,10));
		setLayout(new RowLayout());
		fields = new Hashtable<String,JComponent>();
	}
	
	public String getParam(String name) {
		JComponent jc = fields.get(name);
		if (jc != null) {
			if (jc instanceof JTextField) {
				JTextField jtf = (JTextField)jc;
				return jtf.getText().trim();
			}
			else if (jc instanceof JCheckBox) {
				JCheckBox jcb = (JCheckBox)jc;
				return (jcb.isSelected() ? "checked" : "");
			}
		}
		return "";
	}

	public void addH(String text) {
		addH(text, "center");
	}
	
	public void addH(String text, String align) {
		if (!text.equals("")) {
			add(Box.createVerticalStrut(5));
			add(RowLayout.crlf());
			JLabel h = new JLabel(text);
			h.setFont(headingFont);
			h.setForeground(headingColor);

			if (align.equals("left")) h.setAlignmentX(0.0f);
			else if (align.equals("right")) h.setAlignmentX(1.0f);
			else h.setAlignmentX(0.5f);

			add(h, RowLayout.span(2));
			add(RowLayout.crlf());
			add(Box.createVerticalStrut(5));
			add(RowLayout.crlf());
		}
	}
	
	public void addP(String text) {
		addP(text, "left");
	}

	public void addP(String text, String align) {
		if (!text.equals("")) {
			if (align == null) align = "";
			String[] lines = text.split("\n");
			for (String line : lines) {
				JLabel t = new JLabel(line.trim());
				t.setFont(paragraphFont);
				t.setForeground(paragraphColor);

				if (align.equals("center")) t.setAlignmentX(0.5f);
				else if (align.equals("right")) t.setAlignmentX(1.0f);
				else t.setAlignmentX(0.0f);

				add(t, new Integer(2));
				add(RowLayout.crlf());
			}
		}
	}
	
	public void addParam(String name, String label, String value) {
		addParam(name, label, value, false);
	}
	
	public void addParam(String name, String label, String value, boolean readonly) {
		JLabel jl = new JLabel(label);
		jl.setFont(labelFont);
		add(jl);

		if (!readonly) {
			JTextField jtf = new JTextField(value, fieldWidth);
			jtf.setFont(fieldFont);
			add(jtf);
			fields.put(name, jtf);
		}
		else {
			JLabel jlb = new JLabel(value);
			jlb.setFont(fieldFont);
			add(jlb);
		}

		add(RowLayout.crlf());
	}
	
	public void addCheckBox(String name, String label) {
		JCheckBox jcb = new JCheckBox(label);
		jcb.setFont(labelFont);
		add(jcb, RowLayout.span(2));
		fields.put(name, jcb);
		add(RowLayout.crlf());
	}
	
	public void addSelectableTextArea(String name, String text) {
		JCheckBox jcb = new JCheckBox("");
		jcb.setFont(labelFont);
		jcb.setAlignmentX(1.0f);
		add(jcb);
		JTextArea jta = new JTextArea();
		jta.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		jta.setWrapStyleWord(true);
		jta.setLineWrap(true);
		jta.setText(text);
		jta.setEditable(false);
		jta.setPreferredSize(new Dimension(210,290));
		add(jta);
		fields.put(name, jcb);
		add(RowLayout.crlf());
	}
	
	public void space(int v) {
		add(Box.createVerticalStrut(v));
		add(RowLayout.crlf());
	}	

	public void crlf() {
		add(RowLayout.crlf());
	}

}
