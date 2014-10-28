package be.nabu.libs.types.java;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class BeanInterfaceInstance implements InvocationHandler {

	private Map<String, Object> values = new HashMap<String, Object>();
	
	@Override
	public Object invoke(Object instance, Method method, Object[] args) throws Throwable {
		String name = method.getName();
		if (name.startsWith("get") && (args == null || args.length == 0)) {
			name = getVariableName(name);
			return values.get(name);
		}
		else if (name.startsWith("set") && args != null && args.length == 1) {
			name = getVariableName(name);
			values.put(name, args[0]);
			return null;
		}
		else
			throw new UnsupportedOperationException(method.getName());
	}

	private String getVariableName(String name) {
		name = name.substring(3);
		if (name.isEmpty())
			return null;
		else
			return name.substring(0, 1).toLowerCase() + name.substring(1);
	}
}
