package org.pente.gameDatabase.swing.tools;

import java.io.*;
import java.sql.*;
import java.util.Date;
import java.util.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameDatabase.GameStorerSearchRequestData;
import org.pente.gameDatabase.GameStorerSearchRequestFilterData;
import org.pente.gameDatabase.GameStorerSearchResponseData;
import org.pente.gameDatabase.HttpGameStorerSearcher;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestData;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestFilterData;
import org.pente.gameDatabase.SimpleGameStorerSearchResponseData;
import org.pente.gameDatabase.swing.*;
import org.pente.gameDatabase.swing.importer.GameImporterListener;
import org.pente.gameDatabase.swing.importer.SGFGameFormat;


public class AnalysisTrimmer implements GameImporterListener {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Throwable {
		
		new AnalysisTrimmer();

	}
	private PlunkTree pt;
	public AnalysisTrimmer() throws Throwable {
		FileInputStream in = new FileInputStream(new File("/analysis.sgf"));

		SGFGameFormat gf = new SGFGameFormat("\r\n", "MM/dd/yyyy");
		gf.parse2(in, this);
		
		trim();

		FileWriter out = new FileWriter(new File("/analysis.trim.sgf"));
		SGFGameFormat sgf = new SGFGameFormat();
		sgf.format(pt.getRoot(), 1, pt, out);
		out.close();
	}
	public synchronized void analysisRead(PlunkTree t, String importerName) {
		this.pt = t;
	}
	public void gameRead(PlunkGameData g, String importerName) {
	}
	
	public void trim() {
		trim(pt.getRoot(), 0);
	}
	
	private int trim(PlunkNode n, int pc) {
		if (!n.hasChildren()) return 0;
		else {
			int max = n.getChildCount();
			for (PlunkNode c : n.getChildren()) {
				int r = trim(c, n.getChildCount());
				if (r > max) {
					max = r;
				}
			}
			if (max == 1 && pc == 1) {
				//System.out.println("trim");
				n.getChildren().clear();
			}
			return max;
		}
	}
}
