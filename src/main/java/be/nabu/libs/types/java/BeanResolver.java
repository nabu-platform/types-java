package be.nabu.libs.types.java;

import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeResolver;

public class BeanResolver implements DefinedTypeResolver {

	private Map<String, DefinedType> resolved = new HashMap<String, DefinedType>();
	
	private static BeanResolver instance;
	
	public static BeanResolver getInstance() {
		if (instance == null) {
			instance = new BeanResolver();
		}
		return instance;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public DefinedType resolve(String id) {
		if (!resolved.containsKey(id)) {
			try {
				Class<?> targetType = Thread.currentThread().getContextClassLoader().loadClass(id);
				BeanType<?> beanType = new BeanType(targetType);
				if (beanType.isSimpleType())
					resolved.put(id, new SimpleBeanType(beanType));
				else
					resolved.put(id, beanType);
			}
			catch (ClassNotFoundException e) {
				return null;
			}
		}
		return resolved.get(id);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void register(Class<?> clazz) {
		if (!resolved.containsKey(clazz.getName())) {
			resolved.put(clazz.getName(), new BeanType(clazz));
		}
	}
	
	@SuppressWarnings("unused")
	private void activate() {
		instance = this;
	}
	@SuppressWarnings("unused")
	private void deactivate() {
		instance = null;
	}
}
