package be.nabu.libs.types.java;

import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexContentWrapper;

public class BeanContentWrapper implements ComplexContentWrapper<Object> {

	@SuppressWarnings("rawtypes")
	@Override
	public ComplexContent wrap(Object instance) {
		return new BeanInstance(instance);
	}

	@Override
	public Class<Object> getInstanceClass() {
		return Object.class;
	}

}
