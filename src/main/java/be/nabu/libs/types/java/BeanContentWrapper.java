/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
