package org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state;
import java.lang.Class;
import java.lang.NullPointerException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Generated;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.binding.Augmentation;
import org.opendaylight.yangtools.binding.lib.AbstractAugmentable;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Class that builds {@link CacheState} instances. Overall design of the class is that of a
 * <a href="https://en.wikipedia.org/wiki/Fluent_interface">fluent interface</a>, where method chaining is used.
 *
 * <p>
 * In general, this class is supposed to be used like this template:
 * <pre>
 *   <code>
 *     CacheState createCacheState(int fooXyzzy, int barBaz) {
 *         return new CacheStateBuilder()
 *             .setFoo(new FooBuilder().setXyzzy(fooXyzzy).build())
 *             .setBar(new BarBuilder().setBaz(barBaz).build())
 *             .build();
 *     }
 *   </code>
 * </pre>
 *
 * <p>
 * This pattern is supported by the immutable nature of CacheState, as instances can be freely passed around without
 * worrying about synchronization issues.
 *
 * <p>
 * As a side note: method chaining results in:
 * <ul>
 *   <li>very efficient Java bytecode, as the method invocation result, in this case the Builder reference, is
 *       on the stack, so further method invocations just need to fill method arguments for the next method
 *       invocation, which is terminated by {@link #build()}, which is then returned from the method</li>
 *   <li>better understanding by humans, as the scope of mutable state (the builder) is kept to a minimum and is
 *       very localized</li>
 *   <li>better optimization opportunities, as the object scope is minimized in terms of invocation (rather than
 *       method) stack, making <a href="https://en.wikipedia.org/wiki/Escape_analysis">escape analysis</a> a lot
 *       easier. Given enough compiler (JIT/AOT) prowess, the cost of th builder object can be completely
 *       eliminated</li>
 * </ul>
 *
 * @see CacheState
 *
 */
@Generated("mdsal-binding-generator")
public class CacheStateBuilder {

    private Uint32 _activePairPolicyCount;
    private Uint32 _calculatedPathEntryCount;
    private Uint32 _classificationEntryCount;
    private Uint32 _delegatedLspCount;
    private Uint32 _openflowSwitchCount;
    private Uint32 _policyEvidenceBucketCount;


    Map<Class<? extends Augmentation<CacheState>>, Augmentation<CacheState>> augmentation = Map.of();

    /**
     * Construct an empty builder.
     */
    public CacheStateBuilder() {
        // No-op
    }

    

    /**
     * Construct a builder initialized with state from specified {@link CacheState}.
     *
     * @param base CacheState from which the builder should be initialized
     */
    public CacheStateBuilder(final CacheState base) {
        final var aug = base.augmentations();
        if (!aug.isEmpty()) {
            this.augmentation = new HashMap<>(aug);
        }
        this._activePairPolicyCount = base.getActivePairPolicyCount();
        this._calculatedPathEntryCount = base.getCalculatedPathEntryCount();
        this._classificationEntryCount = base.getClassificationEntryCount();
        this._delegatedLspCount = base.getDelegatedLspCount();
        this._openflowSwitchCount = base.getOpenflowSwitchCount();
        this._policyEvidenceBucketCount = base.getPolicyEvidenceBucketCount();
    }


    private static final class LazyEmpty {
        static final @NonNull CacheState INSTANCE = new CacheStateBuilder().build();
    
        private LazyEmpty() {
            // Hidden on purpose
        }
    }
    
    /**
     * Get empty instance of CacheState.
     *
     * @return An empty {@link CacheState}
     */
    public static @NonNull CacheState empty() {
        return LazyEmpty.INSTANCE;
    }

    /**
     * Return current value associated with the property corresponding to {@link CacheState#getActivePairPolicyCount()}.
     *
     * @return current value
     */
    public Uint32 getActivePairPolicyCount() {
        return _activePairPolicyCount;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link CacheState#getCalculatedPathEntryCount()}.
     *
     * @return current value
     */
    public Uint32 getCalculatedPathEntryCount() {
        return _calculatedPathEntryCount;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link CacheState#getClassificationEntryCount()}.
     *
     * @return current value
     */
    public Uint32 getClassificationEntryCount() {
        return _classificationEntryCount;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link CacheState#getDelegatedLspCount()}.
     *
     * @return current value
     */
    public Uint32 getDelegatedLspCount() {
        return _delegatedLspCount;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link CacheState#getOpenflowSwitchCount()}.
     *
     * @return current value
     */
    public Uint32 getOpenflowSwitchCount() {
        return _openflowSwitchCount;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link CacheState#getPolicyEvidenceBucketCount()}.
     *
     * @return current value
     */
    public Uint32 getPolicyEvidenceBucketCount() {
        return _policyEvidenceBucketCount;
    }

    /**
     * Return the specified augmentation, if it is present in this builder.
     *
     * @param <E$$> augmentation type
     * @param augmentationType augmentation type class
     * @return Augmentation object from this builder, or {@code null} if not present
     * @throws NullPointerException if {@code augmentType} is {@code null}
     */
    @SuppressWarnings({ "unchecked", "checkstyle:methodTypeParameterName"})
    public <E$$ extends Augmentation<CacheState>> E$$ augmentation(Class<E$$> augmentationType) {
        return (E$$) augmentation.get(Objects.requireNonNull(augmentationType));
    }

    
    /**
     * Set the property corresponding to {@link CacheState#getActivePairPolicyCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public CacheStateBuilder setActivePairPolicyCount(final Uint32 value) {
        this._activePairPolicyCount = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link CacheState#getCalculatedPathEntryCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public CacheStateBuilder setCalculatedPathEntryCount(final Uint32 value) {
        this._calculatedPathEntryCount = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link CacheState#getClassificationEntryCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public CacheStateBuilder setClassificationEntryCount(final Uint32 value) {
        this._classificationEntryCount = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link CacheState#getDelegatedLspCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public CacheStateBuilder setDelegatedLspCount(final Uint32 value) {
        this._delegatedLspCount = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link CacheState#getOpenflowSwitchCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public CacheStateBuilder setOpenflowSwitchCount(final Uint32 value) {
        this._openflowSwitchCount = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link CacheState#getPolicyEvidenceBucketCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public CacheStateBuilder setPolicyEvidenceBucketCount(final Uint32 value) {
        this._policyEvidenceBucketCount = value;
        return this;
    }
    
    /**
      * Add an augmentation to this builder's product.
      *
      * @param augmentation augmentation to be added
      * @return this builder
      * @throws NullPointerException if {@code augmentation} is null
      */
    public CacheStateBuilder addAugmentation(Augmentation<CacheState> augmentation) {
        if (!(this.augmentation instanceof HashMap)) {
            this.augmentation = new HashMap<>();
        }
    
        this.augmentation.put(augmentation.implementedInterface(), augmentation);
        return this;
    }
    
    /**
      * Remove an augmentation from this builder's product. If this builder does not track such an augmentation
      * type, this method does nothing.
      *
      * @param augmentationType augmentation type to be removed
      * @return this builder
      */
    public CacheStateBuilder removeAugmentation(Class<? extends Augmentation<CacheState>> augmentationType) {
        if (this.augmentation instanceof HashMap) {
            this.augmentation.remove(augmentationType);
        }
        return this;
    }

    /**
     * A new {@link CacheState} instance.
     *
     * @return A new {@link CacheState} instance.
     */
    public @NonNull CacheState build() {
        return new CacheStateImpl(this);
    }

    private static final class CacheStateImpl
        extends AbstractAugmentable<CacheState>
        implements CacheState {
    
        private final Uint32 _activePairPolicyCount;
        private final Uint32 _calculatedPathEntryCount;
        private final Uint32 _classificationEntryCount;
        private final Uint32 _delegatedLspCount;
        private final Uint32 _openflowSwitchCount;
        private final Uint32 _policyEvidenceBucketCount;
    
        CacheStateImpl(final CacheStateBuilder base) {
            super(base.augmentation);
            this._activePairPolicyCount = base.getActivePairPolicyCount();
            this._calculatedPathEntryCount = base.getCalculatedPathEntryCount();
            this._classificationEntryCount = base.getClassificationEntryCount();
            this._delegatedLspCount = base.getDelegatedLspCount();
            this._openflowSwitchCount = base.getOpenflowSwitchCount();
            this._policyEvidenceBucketCount = base.getPolicyEvidenceBucketCount();
        }
    
        @Override
        public Uint32 getActivePairPolicyCount() {
            return _activePairPolicyCount;
        }
        
        @Override
        public Uint32 getCalculatedPathEntryCount() {
            return _calculatedPathEntryCount;
        }
        
        @Override
        public Uint32 getClassificationEntryCount() {
            return _classificationEntryCount;
        }
        
        @Override
        public Uint32 getDelegatedLspCount() {
            return _delegatedLspCount;
        }
        
        @Override
        public Uint32 getOpenflowSwitchCount() {
            return _openflowSwitchCount;
        }
        
        @Override
        public Uint32 getPolicyEvidenceBucketCount() {
            return _policyEvidenceBucketCount;
        }
    
        
        
        
        
        
    
        private int hash = 0;
        private volatile boolean hashValid = false;
        
        @Override
        public int hashCode() {
            if (hashValid) {
                return hash;
            }
        
            final int result = CacheState.bindingHashCode(this);
            hash = result;
            hashValid = true;
            return result;
        }
    
        @Override
        public boolean equals(Object obj) {
            return CacheState.bindingEquals(this, obj);
        }
    
        @Override
        public String toString() {
            return CacheState.bindingToString(this);
        }
    }
}
