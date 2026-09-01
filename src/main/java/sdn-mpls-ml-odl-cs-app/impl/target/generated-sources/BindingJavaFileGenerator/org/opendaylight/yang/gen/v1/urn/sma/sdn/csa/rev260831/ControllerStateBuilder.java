package org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831;
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
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNode;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNodeKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CacheState;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ControlPlane;
import org.opendaylight.yangtools.binding.Augmentation;
import org.opendaylight.yangtools.binding.lib.AbstractAugmentable;
import org.opendaylight.yangtools.binding.lib.CodeHelpers;
import org.opendaylight.yangtools.yang.common.Uint64;

/**
 * Class that builds {@link ControllerState} instances. Overall design of the class is that of a
 * <a href="https://en.wikipedia.org/wiki/Fluent_interface">fluent interface</a>, where method chaining is used.
 *
 * <p>
 * In general, this class is supposed to be used like this template:
 * <pre>
 *   <code>
 *     ControllerState createControllerState(int fooXyzzy, int barBaz) {
 *         return new ControllerStateBuilder()
 *             .setFoo(new FooBuilder().setXyzzy(fooXyzzy).build())
 *             .setBar(new BarBuilder().setBaz(barBaz).build())
 *             .build();
 *     }
 *   </code>
 * </pre>
 *
 * <p>
 * This pattern is supported by the immutable nature of ControllerState, as instances can be freely passed around without
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
 * @see ControllerState
 *
 */
@Generated("mdsal-binding-generator")
public class ControllerStateBuilder {

    private Map<BgpLsNodeKey, BgpLsNode> _bgpLsNode;
    private CacheState _cacheState;
    private ControlPlane _controlPlane;
    private String _generatedAt;
    private Uint64 _processedPacketCount;


    Map<Class<? extends Augmentation<ControllerState>>, Augmentation<ControllerState>> augmentation = Map.of();

    /**
     * Construct an empty builder.
     */
    public ControllerStateBuilder() {
        // No-op
    }

    

    /**
     * Construct a builder initialized with state from specified {@link ControllerState}.
     *
     * @param base ControllerState from which the builder should be initialized
     */
    public ControllerStateBuilder(final ControllerState base) {
        final var aug = base.augmentations();
        if (!aug.isEmpty()) {
            this.augmentation = new HashMap<>(aug);
        }
        this._bgpLsNode = base.getBgpLsNode();
        this._cacheState = base.getCacheState();
        this._controlPlane = base.getControlPlane();
        this._generatedAt = base.getGeneratedAt();
        this._processedPacketCount = base.getProcessedPacketCount();
    }


    private static final class LazyEmpty {
        static final @NonNull ControllerState INSTANCE = new ControllerStateBuilder().build();
    
        private LazyEmpty() {
            // Hidden on purpose
        }
    }
    
    /**
     * Get empty instance of ControllerState.
     *
     * @return An empty {@link ControllerState}
     */
    public static @NonNull ControllerState empty() {
        return LazyEmpty.INSTANCE;
    }

    /**
     * Return current value associated with the property corresponding to {@link ControllerState#getBgpLsNode()}.
     *
     * @return current value
     */
    public Map<BgpLsNodeKey, BgpLsNode> getBgpLsNode() {
        return _bgpLsNode;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControllerState#getCacheState()}.
     *
     * @return current value
     */
    public CacheState getCacheState() {
        return _cacheState;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControllerState#getControlPlane()}.
     *
     * @return current value
     */
    public ControlPlane getControlPlane() {
        return _controlPlane;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControllerState#getGeneratedAt()}.
     *
     * @return current value
     */
    public String getGeneratedAt() {
        return _generatedAt;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControllerState#getProcessedPacketCount()}.
     *
     * @return current value
     */
    public Uint64 getProcessedPacketCount() {
        return _processedPacketCount;
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
    public <E$$ extends Augmentation<ControllerState>> E$$ augmentation(Class<E$$> augmentationType) {
        return (E$$) augmentation.get(Objects.requireNonNull(augmentationType));
    }

    
    /**
     * Set the property corresponding to {@link ControllerState#getBgpLsNode()} to the specified
     * value.
     *
     * @param values desired value
     * @return this builder
     */
    public ControllerStateBuilder setBgpLsNode(final Map<BgpLsNodeKey, BgpLsNode> values) {
        this._bgpLsNode = values;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControllerState#getCacheState()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControllerStateBuilder setCacheState(final CacheState value) {
        this._cacheState = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControllerState#getControlPlane()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControllerStateBuilder setControlPlane(final ControlPlane value) {
        this._controlPlane = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControllerState#getGeneratedAt()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControllerStateBuilder setGeneratedAt(final String value) {
        this._generatedAt = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControllerState#getProcessedPacketCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControllerStateBuilder setProcessedPacketCount(final Uint64 value) {
        this._processedPacketCount = value;
        return this;
    }
    
    /**
      * Add an augmentation to this builder's product.
      *
      * @param augmentation augmentation to be added
      * @return this builder
      * @throws NullPointerException if {@code augmentation} is null
      */
    public ControllerStateBuilder addAugmentation(Augmentation<ControllerState> augmentation) {
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
    public ControllerStateBuilder removeAugmentation(Class<? extends Augmentation<ControllerState>> augmentationType) {
        if (this.augmentation instanceof HashMap) {
            this.augmentation.remove(augmentationType);
        }
        return this;
    }

    /**
     * A new {@link ControllerState} instance.
     *
     * @return A new {@link ControllerState} instance.
     */
    public @NonNull ControllerState build() {
        return new ControllerStateImpl(this);
    }

    private static final class ControllerStateImpl
        extends AbstractAugmentable<ControllerState>
        implements ControllerState {
    
        private final Map<BgpLsNodeKey, BgpLsNode> _bgpLsNode;
        private final CacheState _cacheState;
        private final ControlPlane _controlPlane;
        private final String _generatedAt;
        private final Uint64 _processedPacketCount;
    
        ControllerStateImpl(final ControllerStateBuilder base) {
            super(base.augmentation);
            this._bgpLsNode = CodeHelpers.emptyToNull(base.getBgpLsNode());
            this._cacheState = base.getCacheState();
            this._controlPlane = base.getControlPlane();
            this._generatedAt = base.getGeneratedAt();
            this._processedPacketCount = base.getProcessedPacketCount();
        }
    
        @Override
        public Map<BgpLsNodeKey, BgpLsNode> getBgpLsNode() {
            return _bgpLsNode;
        }
        
        @Override
        public CacheState getCacheState() {
            return _cacheState;
        }
        
        @Override
        public ControlPlane getControlPlane() {
            return _controlPlane;
        }
        
        @Override
        public String getGeneratedAt() {
            return _generatedAt;
        }
        
        @Override
        public Uint64 getProcessedPacketCount() {
            return _processedPacketCount;
        }
    
        
        @Override
        public CacheState nonnullCacheState() {
            return Objects.requireNonNullElse(getCacheState(), org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CacheStateBuilder.empty());
        }
        
        @Override
        public ControlPlane nonnullControlPlane() {
            return Objects.requireNonNullElse(getControlPlane(), org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ControlPlaneBuilder.empty());
        }
        
        
    
        private int hash = 0;
        private volatile boolean hashValid = false;
        
        @Override
        public int hashCode() {
            if (hashValid) {
                return hash;
            }
        
            final int result = ControllerState.bindingHashCode(this);
            hash = result;
            hashValid = true;
            return result;
        }
    
        @Override
        public boolean equals(Object obj) {
            return ControllerState.bindingEquals(this, obj);
        }
    
        @Override
        public String toString() {
            return ControllerState.bindingToString(this);
        }
    }
}
