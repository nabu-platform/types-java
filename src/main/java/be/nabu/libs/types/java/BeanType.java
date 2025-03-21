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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Future;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.TypeUtils.ComplexTypeValidator;
import be.nabu.libs.types.api.Attribute;
import be.nabu.libs.types.api.CollectionHandler;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Group;
import be.nabu.libs.types.api.JavaClassWrapper;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.api.SneakyEditableBeanInstance;
import be.nabu.libs.types.api.annotation.ComplexTypeDescriptor;
import be.nabu.libs.types.api.annotation.Field;
import be.nabu.libs.types.api.annotation.Transient;
import be.nabu.libs.types.base.AttributeImpl;
import be.nabu.libs.types.base.BaseType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.AliasProperty;
import be.nabu.libs.types.properties.AttributeQualifiedDefaultProperty;
import be.nabu.libs.types.properties.CollectionHandlerProviderProperty;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.ElementQualifiedDefaultProperty;
import be.nabu.libs.types.properties.EnumerationProperty;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.GeneratedProperty;
import be.nabu.libs.types.properties.LengthProperty;
import be.nabu.libs.types.properties.MaxInclusiveProperty;
import be.nabu.libs.types.properties.MaxLengthProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinInclusiveProperty;
import be.nabu.libs.types.properties.MinLengthProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.properties.NamespaceProperty;
import be.nabu.libs.types.properties.NillableProperty;
import be.nabu.libs.types.properties.PatternProperty;
import be.nabu.libs.types.properties.PrimaryKeyProperty;
import be.nabu.libs.types.properties.QualifiedProperty;
import be.nabu.libs.types.properties.RawProperty;
import be.nabu.libs.types.properties.TimeBlock;
import be.nabu.libs.types.properties.TimeBlockProperty;
import be.nabu.libs.types.simple.Date.XSDFormat;
import be.nabu.libs.validator.MultipleValidator;
import be.nabu.libs.validator.api.Validator;

public class BeanType<T> extends BaseType<BeanInstance<T>> implements ComplexType, DefinedType, JavaClassWrapper<T> {
		
	private boolean includeChildrenNotInPropOrder = false;
	
	private Map<String, Method> getters = new HashMap<String, Method>();
	private Map<String, Method> setters = new HashMap<String, Method>();
	private Map<String, Class<?>> actualTypes = new HashMap<String, Class<?>>();
	
	private Map<String, Element<?>> children;
	
	private CollectionHandler handler;
	
	private Class<T> beanClass;
	
	private List<Value<?>> values;
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	private boolean allowVarargsGetters = true;
	
	/**
	 * This contains the name of the element that contains the "value"
	 * If this is present, all other children must be attributes and if so, it really should be exposed as a simple complex type
	 */
	Element<?> valueElement;

	BeanType() {}
	
	public BeanType(Class<T> beanClass) {
		this(beanClass, false);
	}
	
	public BeanType(Class<T> beanClass, boolean includeChildrenNotInPropOrder) {
		this.beanClass = beanClass;
		this.includeChildrenNotInPropOrder = includeChildrenNotInPropOrder;
		loadName();
		loadNamespace();
		loadCollectionName();
	}
	
	public void setCollectionHandler(CollectionHandler handler) {
		this.handler = handler;
	}
	public void unsetCollectionHandler(CollectionHandler handler) {
		this.handler = null;
	}
	public CollectionHandler getCollectionHandler() {
		if (this.handler == null)
			this.handler = CollectionHandlerFactory.getInstance().getHandler();
		return this.handler;
	}
	
	Class<?> getActualType(String name) {
		if (!actualTypes.containsKey(name) && getSuperType() instanceof BeanType)
			return ((BeanType<?>) getSuperType()).getActualType(name);
		else
			return actualTypes.get(name);
	}
	
	Type getGenericType(String name) {
		Method getter = getGetter(name);
		if (getter == null) {
			throw new IllegalArgumentException("Could not find getter for: " + name);
		}
		return getter.getGenericReturnType();
	}
	
	public Class<T> getBeanClass() {
		return beanClass;
	}
	
	@Override
	public String toString() {
		return "BeanType: " + getBeanClass().getName();
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<String, Element<?>> getChildren() {
		if (children == null) {
			synchronized(this) {
				if (children == null) {
					Map<String, Element<?>> children = new LinkedHashMap<String, Element<?>>();
					Map<String, String> mappedNames = new HashMap<String, String>();
					SimpleTypeWrapper wrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
					// this only lists the methods that are actually implemented by this class, not those that are inherited
					for (Method method : getBeanClass().getDeclaredMethods()) {
						if (Modifier.isPublic(method.getModifiers()) && (method.getName().startsWith("get") || method.getName().startsWith("is"))) {
							// it is possible to have 1 parameter which is a varargs in which case we can call the get without any data
							boolean isVarargsGetter = method.getParameterTypes().length == 1 && method.isVarArgs();
							// only methods that do not take parameters
							if ((!isVarargsGetter || !allowVarargsGetters) && method.getParameterTypes().length > 0)
								continue;
							// this is inherent in every object, do not use
							else if (method.getName().equals("getClass"))
								continue;
							// we need a return type for a getter
							else if (method.getReturnType() == null)
								continue;
							// check that it shouldn't be ignored
							else if (method.getAnnotation(XmlTransient.class) != null || method.getAnnotation(Transient.class) != null)
								continue;
							
							String name = method.getName().substring(method.getName().startsWith("get") ? 3 : 2).trim() ;
							if (name.isEmpty())
								continue;
							name = name.substring(0, 1).toLowerCase() + name.substring(1);
							
							logger.debug("Found getter for: {} in {}", name, getBeanClass());
							
							if (getIndicatedName(method) != null) {
								String original = name;
								name = getIndicatedName(method);
								mappedNames.put(original, name);
							}
							
							String namespace = getNamespace(method);
		
							Class<?> returnType = method.getReturnType();
							
							// need to know if it's native (it can not be null then)
							boolean isNative = false;
							// box primitives, they can not be wrapped by simpletype anyway
							if (returnType.getName().equals("int")) {
								returnType = Integer.class;
								isNative = true;
							}
							else if (returnType.getName().equals("float")) {
								returnType = Float.class;
								isNative = true;
							}
							else if (returnType.getName().equals("double")) {
								returnType = Double.class;
								isNative = true;
							}
							else if (returnType.getName().equals("char")) {
								returnType = Character.class;
								isNative = true;
							}
							else if (returnType.getName().equals("short")) {
								returnType = Short.class;
								isNative = true;
							}
							else if (returnType.getName().equals("long")) {
								returnType = Long.class;
								isNative = true;
							}
							else if (returnType.getName().equals("byte")) {
								returnType = Byte.class;
								isNative = true;
							}
							else if (returnType.getName().equals("boolean")) {
								returnType = Boolean.class;
								isNative = true;
							}
							
							// the actual type must be preserved, mostly for collection management
							actualTypes.put(name, returnType);
		
							boolean isList = false;
							CollectionHandlerProvider provider = getCollectionHandler().getHandler(returnType);
							// if it is a list, we need the actual type
							if (provider != null) {
								isList = true;
								try {
									returnType = provider.getComponentType(method.getGenericReturnType());
								}
								catch (IllegalArgumentException e) {
									// proxies don't inherit the generic information from their interfaces, check the interfaces to see if we can find the component type
									Class<?>[] interfaces = getBeanClass().getInterfaces();
									boolean foundInInterfaces = false;
									if (interfaces != null) {
										for (Class<?> clazz : interfaces) {
											try {
												Method methodInterface = clazz.getMethod(method.getName(), method.getParameterTypes());
												returnType = provider.getComponentType(methodInterface.getGenericReturnType());
												foundInInterfaces = true;
											}
											catch (NoSuchMethodException e1) {
												// ignore
											}
											catch (SecurityException e1) {
												// ignore
											}
											// couldn't extract it, stop
											catch (IllegalArgumentException e1) {
												break;
											}
										}
									}
									if (!foundInInterfaces) {
										throw new IllegalArgumentException("Can not get the component type for field '" + name + "': " + method, e);
									}
								}
							}
		
							Element<?> element = null;
							
							SimpleType<?> simpleType = wrapper.wrap(returnType);
							if (simpleType != null) {
								if (isAttribute(method)) {
									element = new AttributeImpl(name, simpleType, this);
									element.setProperty(new ValueImpl(new QualifiedProperty(), isAttributeQualified(getBeanClass())));
								}
								else {
									element = new SimpleElementImpl(name, simpleType, this);
									element.setProperty(new ValueImpl(new QualifiedProperty(), isElementQualified(getBeanClass())));
								}
								// get min/max
								Long min = getMin(method);
								Long max = getMax(method);
								String minDecimal = getMinDecimal(method);
								String maxDecimal = getMaxDecimal(method);
								Integer minLength = getMinLength(method);
								Integer maxLength = getMaxLength(method);
								
								// if min and max are the same, express it as length
								if (minLength != null && maxLength != null && minLength.equals(maxLength))
									element.setProperty(new ValueImpl(new LengthProperty(), minLength));
								else {
									if (minLength != null)
										element.setProperty(new ValueImpl(new MinLengthProperty(), minLength));
									if (maxLength != null)
										element.setProperty(new ValueImpl(new MaxLengthProperty(), maxLength));
								}
								
								if (isGenerated(method)) {
									element.setProperty(new ValueImpl<Boolean>(GeneratedProperty.getInstance(), true));
								}
								
								if (isPrimary(method)) {
									element.setProperty(new ValueImpl<Boolean>(PrimaryKeyProperty.getInstance(), true));
								}
								
								String pattern = getPattern(method);
								if (pattern != null)
									element.setProperty(new ValueImpl(new PatternProperty(), pattern));
								
								// if it's a java.util.date (or extension), check for a schema element type name
								if (Date.class.isAssignableFrom(returnType)) {
									String indicatedSchemaType = getIndicatedSchemaType(method);
									if (indicatedSchemaType != null && XSDFormat.getXSDFormat(indicatedSchemaType) != null) {
										element.setProperty(new ValueImpl(new FormatProperty(), indicatedSchemaType));
									}
								}
								Boolean isFuture = isFuture(method);
								Boolean isPast = isPast(method);
								if (isFuture != null && isFuture)
									element.setProperty(new ValueImpl(new TimeBlockProperty(), TimeBlock.FUTURE));
								else if (isPast != null && isPast)
									element.setProperty(new ValueImpl(new TimeBlockProperty(), TimeBlock.PAST));
								
								Converter converter = ConverterFactory.getInstance().getConverter(); 
								if (min != null)
									element.setProperty(new ValueImpl(new MinInclusiveProperty(), converter.convert(min, returnType)));
								else if (minDecimal != null)
									element.setProperty(new ValueImpl(new MinInclusiveProperty(), converter.convert(minDecimal, returnType)));
								
								if (max != null)
									element.setProperty(new ValueImpl(new MaxInclusiveProperty(), converter.convert(max, returnType)));
								else if (maxDecimal != null)
									element.setProperty(new ValueImpl(new MaxInclusiveProperty(), converter.convert(maxDecimal, returnType)));
							}
							else {
								element = new ComplexElementImpl(name, (ComplexType) BeanResolver.getInstance().resolve(returnType), this);
								element.setProperty(new ValueImpl(new AttributeQualifiedDefaultProperty(), isAttributeQualified(returnType)));
								element.setProperty(new ValueImpl(new ElementQualifiedDefaultProperty(), isElementQualified(returnType)));
								element.setProperty(new ValueImpl(new QualifiedProperty(), isElementQualified(getBeanClass())));
							}
							
							// if we have a collection provider, set it as a property for instantiation later
							if (provider != null) {
								element.setProperty(new ValueImpl(new CollectionHandlerProviderProperty(), provider));
							}
							
							if (namespace != null && !NamespaceProperty.DEFAULT_NAMESPACE.equals(namespace))
								element.setProperty(new ValueImpl(NamespaceProperty.getInstance(), namespace));
							
							if (method.getAnnotation(XmlValue.class) != null)
								valueElement = element;
							// make sure the nillable is explicitly set correctly
							boolean nillable = false;
							if (!isNative && isNillable(method)) {
								element.setProperty(new ValueImpl(NillableProperty.getInstance(), true));
								nillable = true;
							}
							else {
								element.setProperty(new ValueImpl(NillableProperty.getInstance(), false));
							}
						
							String alias = getAlias(method);
							if (alias != null) {
								element.setProperty(new ValueImpl<String>(AliasProperty.getInstance(), alias));
							}
							
							Integer minOccurs = getMinOccurs(method);
							Integer maxOccurs = getMaxOccurs(method);
							if (minOccurs != null) {
								element.setProperty(new ValueImpl(MinOccursProperty.getInstance(), minOccurs));
							}
							else if (nillable) {
								element.setProperty(new ValueImpl(MinOccursProperty.getInstance(), 0));
							}
							if (maxOccurs != null)
								element.setProperty(new ValueImpl(MaxOccursProperty.getInstance(), maxOccurs));
							else if (isList)
								element.setProperty(new ValueImpl(MaxOccursProperty.getInstance(), 0));
							
							boolean raw = isRaw(method);
							if (raw) {
								element.setProperty(new ValueImpl<Boolean>(RawProperty.getInstance(), true));
							}
							
							getters.put(element.getName(), method);
							
							children.put(element.getName(), element);
						}
						// there might be more getters than setters with the following code but it doesn't matter as they won't get called
						else if (method.getName().startsWith("set")) {
							String name = method.getName().substring(3).trim();
							if (name.isEmpty()) {
								continue;
							}
							// first check the getter, it may have been mapped to another name
							try {
								Method getterMethod = getMethod(getBeanClass(), "get" + name);
								if (getterMethod == null) {
									getterMethod = getMethod(getBeanClass(), "is" + name);
								}
								String mappedName = getterMethod == null ? null : getIndicatedName(getterMethod);
								if (mappedName != null) {
									name = mappedName;
								}
								// if not mapped, camelcase it
								else {
									name = name.substring(0, 1).toLowerCase() + name.substring(1);					
								}
							}
							catch (SecurityException e) {
								// do nothing
							}
							setters.put(name, method);
						}
					}
					String [] propOrder = getPropOrder(getBeanClass());
					if (propOrder != null) {
						children = orderChildren(children, propOrder, mappedNames);
					}
					else {
						children = orderChildren(children, mappedNames);
					}
					this.children = children;
				}
			}
		}
		return children;
	}
	
	private String getAlias(Method method) {
		Field field = method.getAnnotation(Field.class);
		if (field != null && !field.alias().trim().isEmpty()) {
			return field.alias();
		}
		return null;
	}
	
	private boolean isRaw(Method method) {
		Field field = method.getAnnotation(Field.class);
		return field == null ? false : field.raw();
	}

	private Method getMethod(Class<?> clazz, String name) {
		for (Method method : clazz.getDeclaredMethods()) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		return null;
	}
	
	// by default we order alphabetically to be predictable and user friendly
	private Map<String, Element<?>> orderChildren(Map<String, Element<?>> children, Map<String, String> mappedNames) {
		ArrayList<String> names = new ArrayList<String>(children.keySet());
		Collections.sort(names);
		return orderChildren(children, names.toArray(new String[names.size()]), mappedNames);
	}
	
	private Map<String, Element<?>> orderChildren(Map<String, Element<?>> children, String [] propOrder, Map<String, String> mappedNames) {
		List<String> availableChildren = new ArrayList<String>(children.keySet());
		Map<String, Element<?>> orderedChildren = new LinkedHashMap<String, Element<?>>();
		for (String childName : propOrder) {
			if (mappedNames.containsKey(childName) && availableChildren.contains(mappedNames.get(childName))) {
				childName = mappedNames.get(childName);
			}
			availableChildren.remove(childName);
			if (!children.containsKey(childName))
				throw new IllegalArgumentException("The annotation 'propOrder' contains a child element that does not exist: " + childName + " !# " + children.keySet());
			orderedChildren.put(childName, children.get(childName));
		}
		if (includeChildrenNotInPropOrder) {
			for (String childName : availableChildren)
				orderedChildren.put(childName, children.get(childName));
		}
		return orderedChildren;
	}
	
	public boolean isSimpleType() {
		if (valueElement != null) {
			for (String name : getChildren().keySet()) {
				if (!valueElement.getName().equals(name) && !(getChildren().get(name) instanceof Attribute))
					return false;
			}
			return true;
		}
		else
			return false;
	}

	protected void loadCollectionName() {
		ComplexTypeDescriptor descriptor = getBeanClass().getAnnotation(ComplexTypeDescriptor.class);
		if (descriptor != null && descriptor.collectionName() != null && !descriptor.collectionName().trim().isEmpty()) {
			setProperty(new ValueImpl<String>(CollectionNameProperty.getInstance(), descriptor.collectionName()));
		}
	}
	
	protected void loadName() {
		ComplexTypeDescriptor descriptor = getBeanClass().getAnnotation(ComplexTypeDescriptor.class);
		if (descriptor != null && descriptor.name() != null && !descriptor.name().trim().isEmpty()) {
			setProperty(new ValueImpl<String>(NameProperty.getInstance(), descriptor.name()));
		}
		else {
			XmlRootElement annotation = getBeanClass().getAnnotation(XmlRootElement.class);
			if (annotation == null || annotation.name() == null || annotation.name().equals(NamespaceProperty.DEFAULT_NAMESPACE) || annotation.name().trim().isEmpty()) {
				String name = getBeanClass().getName().replaceAll(".*\\.", "");
				name = name.substring(0, 1).toLowerCase() + name.substring(1);
				setProperty(new ValueImpl<String>(NameProperty.getInstance(), name));
			}
			else {
				setProperty(new ValueImpl<String>(NameProperty.getInstance(), annotation.name()));
			}
		}
	}
	
	@Override
	public String getName(Value<?>...values) {
		String valueName = ValueUtils.getValue(new NameProperty(), values);
		if (valueName != null) {
			return valueName;
		}
		return ValueUtils.getValue(new NameProperty(), getProperties());
	}

	protected void loadNamespace() {
		ComplexTypeDescriptor descriptor = getBeanClass().getAnnotation(ComplexTypeDescriptor.class);
		if (descriptor != null && descriptor.namespace() != null && !descriptor.namespace().trim().isEmpty()) {
			setProperty(new ValueImpl<String>(NamespaceProperty.getInstance(), descriptor.namespace()));
		}
		else {
			XmlRootElement rootAnnotation = getBeanClass().getAnnotation(XmlRootElement.class);
			if (rootAnnotation != null && !NamespaceProperty.DEFAULT_NAMESPACE.equals(rootAnnotation.namespace())) {
				setProperty(new ValueImpl<String>(NamespaceProperty.getInstance(), rootAnnotation.namespace()));
			}
			else {
				XmlSchema annotation = getBeanClass().getPackage() == null ? null : getBeanClass().getPackage().getAnnotation(XmlSchema.class);
				if (annotation != null && !NamespaceProperty.DEFAULT_NAMESPACE.equals(annotation.namespace())) {
					setProperty(new ValueImpl<String>(NamespaceProperty.getInstance(), annotation.namespace()));
				}
			}
		}
	}
	
	@Override
	public String getNamespace(Value<?>...values) {
		String valueName = ValueUtils.getValue(NamespaceProperty.getInstance(), values);
		if (valueName != null && !NamespaceProperty.DEFAULT_NAMESPACE.equals(valueName)) {
			return valueName;
		}
		return ValueUtils.getValue(NamespaceProperty.getInstance(), getProperties());
	}
	
	protected String getIndicatedSchemaType(Method method) {
		// currently only support for xml schema types
		XmlSchemaType annotation = method.getAnnotation(XmlSchemaType.class);
		return annotation == null || (annotation.namespace() != null && !annotation.namespace().equals("http://www.w3.org/2001/XMLSchema")) ? null : annotation.name();
	}
	
	protected String [] getPropOrder(Class<?> clazz) {
		ComplexTypeDescriptor descriptor = clazz.getAnnotation(ComplexTypeDescriptor.class);
		if (descriptor != null && descriptor.propOrder() != null && descriptor.propOrder().length > 0) {
			return descriptor.propOrder();
		}
		XmlType annotation = clazz.getAnnotation(XmlType.class);
		return annotation == null ? null : annotation.propOrder();
	}
	
	protected String getIndicatedName(Method method) {
		Field field = method.getAnnotation(Field.class);
		if (field != null && !field.name().trim().isEmpty()) {
			return field.name();
		}
		XmlElement elementAnnotation = method.getAnnotation(XmlElement.class);
		String name = elementAnnotation == null ? null : elementAnnotation.name();
		if (name == null) {
			XmlAttribute attributeAnnotation = method.getAnnotation(XmlAttribute.class);
			name = attributeAnnotation == null ? null : attributeAnnotation.name();
		}
		return name == null || name.equals("##default") ? null : name;
	}
	
	protected String getNamespace(Method method) {
		Field field = method.getAnnotation(Field.class);
		if (field != null && !field.namespace().trim().isEmpty()) {
			return field.namespace();
		}
		XmlElement elementAnnotation = method.getAnnotation(XmlElement.class);
		String namespace = elementAnnotation == null ? null : elementAnnotation.namespace();
		if (namespace == null) {
			XmlAttribute attributeAnnotation = method.getAnnotation(XmlAttribute.class);
			namespace = attributeAnnotation == null ? null : attributeAnnotation.namespace();
		}
		return namespace == null || namespace.equals(NamespaceProperty.DEFAULT_NAMESPACE) ? null : namespace;
	}

	protected boolean isElementQualified(Class<?> clazz) {
		XmlSchema annotation = clazz.getPackage() == null ? null : clazz.getPackage().getAnnotation(XmlSchema.class);
		if (annotation == null)
			return false;
		else
			return XmlNsForm.QUALIFIED.equals(annotation.elementFormDefault());
	}

	protected boolean isNillable(Method method) {
		Field field = method.getAnnotation(Field.class);
		// must work correctly together with not null
		if (field != null && field.minOccurs() != 0) {
			return false;
		}
		return method.getAnnotation(NotNull.class) == null;
	}
	
	protected boolean isGenerated(Method method) {
		Field field = method.getAnnotation(Field.class);
		return field != null && field.generated();
	}
	
	protected boolean isPrimary(Method method) {
		Field field = method.getAnnotation(Field.class);
		return field != null && field.primary();
	}
	
	protected Long getMin(Method method) {
		Field field = method.getAnnotation(Field.class);
		if (field != null && field.min() != 0) {
			return field.min();
		}
		Min annotation = method.getAnnotation(Min.class);
		return annotation == null ? null : annotation.value();
	}
	protected Long getMax(Method method) {
		Field field = method.getAnnotation(Field.class);
		if (field != null && field.max() != 0) {
			return field.max();
		}
		Max annotation = method.getAnnotation(Max.class);
		return annotation == null ? null : annotation.value();
	}
	protected String getMinDecimal(Method method) {
		DecimalMin annotation = method.getAnnotation(DecimalMin.class);
		return annotation == null ? null : annotation.value();
	}
	protected String getMaxDecimal(Method method) {
		DecimalMax annotation = method.getAnnotation(DecimalMax.class);
		return annotation == null ? null : annotation.value();
	}
	
	protected Integer getMinOccurs(Method method) {
		if (Collection.class.isAssignableFrom(method.getReturnType()) || Object[].class.isAssignableFrom(method.getReturnType())) {
			Field field = method.getAnnotation(Field.class);
			if (field != null && field.minOccurs() != 0) {
				return field.minOccurs();
			}
			Size annotation = method.getAnnotation(Size.class);
			return annotation == null ? null : annotation.min();
		}
		else
			return null;
	}
	protected Integer getMaxOccurs(Method method) {
		if (Collection.class.isAssignableFrom(method.getReturnType()) || Object[].class.isAssignableFrom(method.getReturnType())) {
			Field field = method.getAnnotation(Field.class);
			if (field != null && field.maxOccurs() != 0) {
				return field.maxOccurs();
			}
			Size annotation = method.getAnnotation(Size.class);
			return annotation == null ? 0 : annotation.max();
		}
		else
			return null;
	}
	protected Integer getMinLength(Method method) {
		if (CharSequence.class.isAssignableFrom(method.getReturnType())) {
			Field field = method.getAnnotation(Field.class);
			if (field != null && field.minLength() != 0) {
				return field.minLength();
			}
			Size annotation = method.getAnnotation(Size.class);
			return annotation == null ? null : annotation.min();
		}
		else
			return null;
	}
	protected Integer getMaxLength(Method method) {
		if (CharSequence.class.isAssignableFrom(method.getReturnType())) {
			Field field = method.getAnnotation(Field.class);
			if (field != null && field.maxLength() != 0) {
				return field.maxLength();
			}
			Size annotation = method.getAnnotation(Size.class);
			return annotation == null ? null : annotation.max();
		}
		else
			return null;
	}
	protected String getPattern(Method method) {
		if (CharSequence.class.isAssignableFrom(method.getReturnType())) {
			Field field = method.getAnnotation(Field.class);
			if (field != null && !field.pattern().trim().isEmpty()) {
				return field.pattern();
			}
			Pattern annotation = method.getAnnotation(Pattern.class);
			return annotation == null ? null : annotation.regexp();
		}
		else
			return null;
	}
	protected Boolean isFuture(Method method) {
		if (Date.class.isAssignableFrom(method.getReturnType())) {
			Future annotation = method.getAnnotation(Future.class);
			return annotation != null;
		}
		else
			return null;
	}
	protected Boolean isPast(Method method) {
		if (Date.class.isAssignableFrom(method.getReturnType())) {
			Past annotation = method.getAnnotation(Past.class);
			return annotation != null;
		}
		else
			return null;
	}

	protected boolean isList(Method method) {
		return method.getReturnType().isArray() || Collection.class.isAssignableFrom(method.getReturnType());
	}

	protected boolean isAttributeQualified(Class<?> clazz) {
		XmlSchema annotation = clazz.getPackage() == null ? null : clazz.getPackage().getAnnotation(XmlSchema.class);
		if (annotation == null)
			return false;
		else
			return XmlNsForm.QUALIFIED.equals(annotation.attributeFormDefault());
	}
	
	protected boolean isAttribute(Method method) {
		return method.getAnnotation(XmlAttribute.class) != null;
	}

	@Override
	public ComplexType getSuperType() {
		// if we implement an interface and the direct parent is object, report the interface as the parent
		if ((getBeanClass().isInterface() || getBeanClass().getSuperclass() == null || getBeanClass().getSuperclass().equals(Object.class)) && getBeanClass().getInterfaces().length > 0) {
			return (ComplexType) BeanResolver.getInstance().resolve(getBeanClass().getInterfaces()[0]);
		}
		return getBeanClass().getSuperclass() == null ? null : (ComplexType) BeanResolver.getInstance().resolve(getBeanClass().getSuperclass());
	}

	@Override
	public BeanInstance<T> newInstance() {
		// use the context class loader, the actual class may reside at a point that does not see the full class hierarchy
		if (getBeanClass().isInterface())
			return new BeanInstance<T>(this, Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?> [] { getBeanClass(), SneakyEditableBeanInstance.class }, new BeanInterfaceInstance(this)));
		else {
			try {
				return new BeanInstance<T>(this, getBeanClass().newInstance());
			}
			catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			catch (InstantiationException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	Method getSetter(String name) {
		if (!setters.containsKey(name) && getSuperType() instanceof BeanType)
			return ((BeanType<?>) getSuperType()).getSetter(name);
		else
			return setters.get(name);
	}
	
	Method getGetter(String name) {
		if (!getters.containsKey(name) && getSuperType() instanceof BeanType)
			return ((BeanType<?>) getSuperType()).getGetter(name);
		else
			return getters.get(name);
	}
	
	public Annotation[] getAnnotations(String name) {
		Method getter = getGetter(name);
		return getter == null ? null : getter.getAnnotations();
	}
	
	@Override
	public Iterator<Element<?>> iterator() {
		return getChildren().values().iterator();
	}

	@Override
	public Element<?> get(String path) {
		return getChildren().containsKey(path) 
			? getChildren().get(path)
			: TypeUtils.getChild(this, path);
	}

	@Override
	public boolean equals(Object object) {
		return object instanceof BeanType && ((BeanType<?>) object).getBeanClass().equals(getBeanClass());
	}
	
	@Override
	public int hashCode() {
		return getBeanClass().hashCode();
	}

	@Override
	public String getId() {
		if (Proxy.isProxyClass(getBeanClass()) && getBeanClass().getInterfaces().length > 0) {
			return getBeanClass().getInterfaces()[0].getName();
		}
		return getBeanClass().getName();
	}

	@Override
	public Boolean isAttributeQualified(Value<?>... values) {
		if (ValueUtils.contains(new AttributeQualifiedDefaultProperty(), values))
			return ValueUtils.getValue(new AttributeQualifiedDefaultProperty(), values);
		else
			return isAttributeQualified(getBeanClass());
	}

	@Override
	public Boolean isElementQualified(Value<?>... values) {
		if (ValueUtils.contains(new ElementQualifiedDefaultProperty(), values))
			return ValueUtils.getValue(new ElementQualifiedDefaultProperty(), values);
		else
			return isElementQualified(getBeanClass());
	}

	/**
	 * Currently we do not support the JAXB way of using choice elements
	 */
	@Override
	public Group[] getGroups() {
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Validator<BeanInstance<T>> createValidator(Value<?>... values) {
		List<Validator> validators = new ArrayList<Validator>();
		validators.add(super.createValidator(values));
		validators.add(new BeanTypeValidator(this));
		return new MultipleValidator<BeanInstance<T>>(validators.toArray(new Validator[validators.size()]));
	}
	
	public static class BeanTypeValidator extends ComplexTypeValidator {

		public BeanTypeValidator(ComplexType type) {
			super(type);
		}

		@SuppressWarnings("rawtypes")
		@Override
		protected ComplexContent convert(Object instance) {
			return instance == null ? null : new BeanInstance(instance);
		}
	
	}

	@Override
	public Value<?>[] getProperties() {
		if (!Enum.class.isAssignableFrom(getBeanClass())) {
			return super.getProperties();
		}
		else if (values == null) {
			synchronized(this) {
				if (values == null) {
					values = new ArrayList<Value<?>>(Arrays.asList(super.getProperties()));
					// add enumeration constants
					values.add(new ValueImpl<List<T>>(new EnumerationProperty<T>(), Arrays.asList(getBeanClass().getEnumConstants())));
				}
			}
		}
		return values.toArray(new Value[values.size()]);
	}

	@Override
	public Class<T> getWrappedClass() {
		return getBeanClass();
	}
}
