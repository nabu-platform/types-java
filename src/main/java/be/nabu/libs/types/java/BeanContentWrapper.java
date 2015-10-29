package be.nabu.libs.types.java;

import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexContentWrapper;
import be.nabu.libs.types.api.DefinedType;

public class BeanContentWrapper implements ComplexContentWrapper<Object> {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public ComplexContent wrap(Object instance) {
		if (instance != null) {
			DefinedType resolved = BeanResolver.getInstance().resolve(instance.getClass());
			if (resolved instanceof BeanType) {
				return new BeanInstance((BeanType) resolved, instance);
			}
		}
		return null;
	}

	@Override
	public Class<Object> getInstanceClass() {
		return Object.class;
	}

}
