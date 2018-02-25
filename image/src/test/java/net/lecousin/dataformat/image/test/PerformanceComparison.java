package net.lecousin.dataformat.image.test;

public class PerformanceComparison {
	
	public static <T> void compare(int occurences, T input, NamedTest<T>... tests) {
		System.gc();
		// run each a first time to ensure class loading and initializations have been performed
		int maxNameLength = 0;
		System.out.println("Run tests a first time for initialization");
		for (NamedTest<T> test : tests) {
			test.runTest(input);
			if (test.getName().length() > maxNameLength)
				maxNameLength = test.getName().length();
		}
		// evaluate
		System.out.println("Evaluating performance");
		long start, end, total = 0, min=0, max=0;
		for (NamedTest<T> test : tests) {
			System.gc();
			// run a first time
			test.runTest(input);
			// and evaluate
			for (int i = 0; i < occurences+2; ++i) {
				start = System.nanoTime();
				test.runTest(input);
				end = System.nanoTime();
				if (i == 0) {
					min = max = total = end-start;
				} else {
					end -= start;
					if (end < min) min = end;
					if (end > max) max = end;
					total += end;
				}
			}
			total -= min+max;
			total /= occurences;
			StringBuilder s = new StringBuilder();
			s.append(test.getName());
			while (s.length() < maxNameLength) s.append(' ');
			s.append(": min = ").append(min);
			while (s.length() < maxNameLength + 20) s.append(' ');
			s.append(" max = ").append(max);
			while (s.length() < maxNameLength + 40) s.append(' ');
			s.append(" avg = ").append(total);
			System.out.println(s.toString());
		}
	}
	
}
