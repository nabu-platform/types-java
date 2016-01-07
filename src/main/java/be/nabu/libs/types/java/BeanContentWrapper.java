package be.nabu.libs.types.java;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexContentWrapper;
import be.nabu.libs.types.api.DefinedType;

public class BeanContentWrapper implements ComplexContentWrapper<Object> {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public ComplexContent wrap(Object instance) {
		if (instance != null) {
			// in cases of a proxy class coming in, we need to know the original interface we wrap around because interfaces are _not_ listed in the supertype
			// otherwise the proxy implementation can never be linked back to the original interface
			if (Proxy.isProxyClass(instance.getClass())) {
				InvocationHandler invocationHandler = Proxy.getInvocationHandler(instance);
				if (invocationHandler instanceof BeanInterfaceInstance) {
					return new BeanInstance(((BeanInterfaceInstance) invocationHandler).getOriginalType(), instance);
				}
			}
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
