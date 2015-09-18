package be.nabu.libs.types.java;

import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexContentWrapper;
import be.nabu.libs.types.api.DefinedType;

public class BeanContentWrapper implements ComplexContentWrapper<Object> {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public ComplexContent wrap(Object instance) {
		DefinedType resolved = DefinedTypeResolverFactory.getInstance().getResolver().resolve(instance.getClass().getName());
		if (resolved instanceof BeanType) {
			return new BeanInstance((BeanType) resolved, instance);
		}
		return null;
	}

	@Override
	public Class<Object> getInstanceClass() {
		return Object.class;
	}

}
