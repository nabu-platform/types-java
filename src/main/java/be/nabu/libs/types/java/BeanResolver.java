package be.nabu.libs.types.java;

import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeResolver;

public class BeanResolver implements DefinedTypeResolver {

	private Map<String, DefinedType> resolved = new HashMap<String, DefinedType>();
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public DefinedType resolve(String id) {
		if (!resolved.containsKey(id)) {
			try {
				Class<?> targetType = Class.forName(id);
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
}
