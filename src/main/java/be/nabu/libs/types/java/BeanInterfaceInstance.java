package be.nabu.libs.types.java;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class BeanInterfaceInstance implements InvocationHandler, Serializable {

	private static final long serialVersionUID = 1L;
	
	private Map<String, Object> values = new HashMap<String, Object>();

	private BeanType<?> originalType;

	public BeanInterfaceInstance(BeanType<?> originalType) {
		this.originalType = originalType;
	}
	
	@Override
	public Object invoke(Object instance, Method method, Object[] args) throws Throwable {
		String name = method.getName();
		if ((name.startsWith("get") || name.startsWith("is")) && (args == null || args.length == 0 || (args.length == 1 && method.isVarArgs()))) {
			name = getVariableName(name);
			return values.get(name);
		}
		else if (name.startsWith("set") && args != null && args.length == 1) {
			name = getVariableName(name);
			values.put(name, args[0]);
			return null;
		}
		// sneaky set!
		else if (name.equals("__set") && args.length == 2) {
			values.put((String) args[0], args[1]);
			return null;
		}
		// support for equals...
		else if (name.equals("equals") && args.length == 1) {
			// @2021-06-21: the actual equals is only true if they are the same object
			// you can override that to have better logic, but because we are using proxies on interfaces, you (almost) definitely did not override the equals
			// because we are working with beans, we could check deeper and see if the content is the same?
			// however, in the past this just said "return instance.equals(args[0])" without checking deeper
			// that could (in some cases) however end up in a recursive stackoverflowerror, presumably because you are equalling the item to itself?
			// so for now, we'll do an identity check, in the future we might add a bean-aware depth check of all the values
//			return instance.equals(args[0]);
			return instance == args[0];
		}
		// allow for default methods like 'toString()'
		else {
			// not sure why it is "this" instead of "instance", but changing it now results in horrible recursion
			// to be checked at a later date, just want to have support for equals for now
			return method.invoke(this, args);
		}
	}

	private String getVariableName(String name) {
		name = name.startsWith("get") ? name.substring(3) : name.substring(2);
		if (name.isEmpty())
			return null;
		else
			return name.substring(0, 1).toLowerCase() + name.substring(1);
	}

	public BeanType<?> getOriginalType() {
		return originalType;
	}
	
}
