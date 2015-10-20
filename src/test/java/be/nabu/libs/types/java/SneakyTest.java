package be.nabu.libs.types.java;

import junit.framework.TestCase;

public class SneakyTest extends TestCase {

	public void testSneakySet() {
		BeanInstance<Test> instance = new BeanType<Test>(Test.class).newInstance();
		instance.set("name", "bob");
		assertEquals("bob", instance.get("name"));
	}
	
	public static interface Test {
		public String getName();
	}
}
