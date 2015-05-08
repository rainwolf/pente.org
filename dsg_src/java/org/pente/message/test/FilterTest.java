package org.pente.message.test;

import com.jivesoftware.base.*;
import com.jivesoftware.base.filter.*;

public class FilterTest {

	class CustomFilter implements Filter {
		public CustomFilter() {}
		public String applyFilter(String arg0, int arg1, FilterChain arg2) {
			return arg0;
		}
		public String getName() {
			return "CustomFilter";
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		FilterChain filters = new FilterChain(
			"stuff", 1, new Filter[] { new Newline(), new FilterTest().new CustomFilter() }, 
			new long[] { 1, 1  });

		String orig = "This is a test\r\n, newline encountered.";
		String filtered = filters.applyFilters(0, orig);
		
		System.out.println("orig = " + orig);
		System.out.println("filtered = " + filtered);
	}

}
