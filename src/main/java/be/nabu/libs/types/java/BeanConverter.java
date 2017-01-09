package be.nabu.libs.types.java;

import be.nabu.libs.types.api.TypeConverter;
import be.nabu.libs.types.api.TypeInstance;

public class BeanConverter implements TypeConverter {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public <T> T convert(Object instance, TypeInstance from, TypeInstance to) {
		if (instance == null || !(to.getType() instanceof BeanType)) {
			return null;
		}
		Object original;
		if (instance instanceof BeanInstance) {
			original = ((BeanInstance) instance).getUnwrapped();
		}
		else {
			original = instance;
		}
		if (((BeanType) to.getType()).getBeanClass().isAssignableFrom(original.getClass())) {
			return (T) new BeanInstance((BeanType) to.getType(), original);
		}
		return null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public boolean canConvert(TypeInstance from, TypeInstance to) {
		return from.getType() instanceof BeanType && to.getType() instanceof BeanType
				&& ((BeanType) to.getType()).getBeanClass().isAssignableFrom(((BeanType) from.getType()).getBeanClass());
	}

}
