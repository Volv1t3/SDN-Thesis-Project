package org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state;
import java.lang.Boolean;
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
import org.opendaylight.yangtools.yang.common.Uint64;

/**
 * Class that builds {@link ControlPlane} instances. Overall design of the class is that of a
 * <a href="https://en.wikipedia.org/wiki/Fluent_interface">fluent interface</a>, where method chaining is used.
 *
 * <p>
 * In general, this class is supposed to be used like this template:
 * <pre>
 *   <code>
 *     ControlPlane createControlPlane(int fooXyzzy, int barBaz) {
 *         return new ControlPlaneBuilder()
 *             .setFoo(new FooBuilder().setXyzzy(fooXyzzy).build())
 *             .setBar(new BarBuilder().setBaz(barBaz).build())
 *             .build();
 *     }
 *   </code>
 * </pre>
 *
 * <p>
 * This pattern is supported by the immutable nature of ControlPlane, as instances can be freely passed around without
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
 * @see ControlPlane
 *
 */
@Generated("mdsal-binding-generator")
public class ControlPlaneBuilder {

    private Boolean _closed;
    private Boolean _ready;
    private Boolean _topologyFresh;
    private String _topologyFreshUntil;
    private String _topologyId;
    private String _topologyLastFailure;
    private String _topologyLastRefreshAttempt;
    private String _topologyLastSuccessfulRefresh;
    private Uint64 _topologyRefreshFailureCount;
    private Boolean _topologyRefreshInProgress;
    private Uint64 _topologyRefreshSuccessCount;
    private Uint64 _topologyTtlMillis;


    Map<Class<? extends Augmentation<ControlPlane>>, Augmentation<ControlPlane>> augmentation = Map.of();

    /**
     * Construct an empty builder.
     */
    public ControlPlaneBuilder() {
        // No-op
    }

    

    /**
     * Construct a builder initialized with state from specified {@link ControlPlane}.
     *
     * @param base ControlPlane from which the builder should be initialized
     */
    public ControlPlaneBuilder(final ControlPlane base) {
        final var aug = base.augmentations();
        if (!aug.isEmpty()) {
            this.augmentation = new HashMap<>(aug);
        }
        this._closed = base.getClosed();
        this._ready = base.getReady();
        this._topologyFresh = base.getTopologyFresh();
        this._topologyFreshUntil = base.getTopologyFreshUntil();
        this._topologyId = base.getTopologyId();
        this._topologyLastFailure = base.getTopologyLastFailure();
        this._topologyLastRefreshAttempt = base.getTopologyLastRefreshAttempt();
        this._topologyLastSuccessfulRefresh = base.getTopologyLastSuccessfulRefresh();
        this._topologyRefreshFailureCount = base.getTopologyRefreshFailureCount();
        this._topologyRefreshInProgress = base.getTopologyRefreshInProgress();
        this._topologyRefreshSuccessCount = base.getTopologyRefreshSuccessCount();
        this._topologyTtlMillis = base.getTopologyTtlMillis();
    }


    private static final class LazyEmpty {
        static final @NonNull ControlPlane INSTANCE = new ControlPlaneBuilder().build();
    
        private LazyEmpty() {
            // Hidden on purpose
        }
    }
    
    /**
     * Get empty instance of ControlPlane.
     *
     * @return An empty {@link ControlPlane}
     */
    public static @NonNull ControlPlane empty() {
        return LazyEmpty.INSTANCE;
    }

    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getClosed()}.
     *
     * @return current value
     */
    public Boolean getClosed() {
        return _closed;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getReady()}.
     *
     * @return current value
     */
    public Boolean getReady() {
        return _ready;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyFresh()}.
     *
     * @return current value
     */
    public Boolean getTopologyFresh() {
        return _topologyFresh;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyFreshUntil()}.
     *
     * @return current value
     */
    public String getTopologyFreshUntil() {
        return _topologyFreshUntil;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyId()}.
     *
     * @return current value
     */
    public String getTopologyId() {
        return _topologyId;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyLastFailure()}.
     *
     * @return current value
     */
    public String getTopologyLastFailure() {
        return _topologyLastFailure;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyLastRefreshAttempt()}.
     *
     * @return current value
     */
    public String getTopologyLastRefreshAttempt() {
        return _topologyLastRefreshAttempt;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyLastSuccessfulRefresh()}.
     *
     * @return current value
     */
    public String getTopologyLastSuccessfulRefresh() {
        return _topologyLastSuccessfulRefresh;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyRefreshFailureCount()}.
     *
     * @return current value
     */
    public Uint64 getTopologyRefreshFailureCount() {
        return _topologyRefreshFailureCount;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyRefreshInProgress()}.
     *
     * @return current value
     */
    public Boolean getTopologyRefreshInProgress() {
        return _topologyRefreshInProgress;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyRefreshSuccessCount()}.
     *
     * @return current value
     */
    public Uint64 getTopologyRefreshSuccessCount() {
        return _topologyRefreshSuccessCount;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link ControlPlane#getTopologyTtlMillis()}.
     *
     * @return current value
     */
    public Uint64 getTopologyTtlMillis() {
        return _topologyTtlMillis;
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
    public <E$$ extends Augmentation<ControlPlane>> E$$ augmentation(Class<E$$> augmentationType) {
        return (E$$) augmentation.get(Objects.requireNonNull(augmentationType));
    }

    
    /**
     * Set the property corresponding to {@link ControlPlane#getClosed()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setClosed(final Boolean value) {
        this._closed = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getReady()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setReady(final Boolean value) {
        this._ready = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyFresh()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyFresh(final Boolean value) {
        this._topologyFresh = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyFreshUntil()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyFreshUntil(final String value) {
        this._topologyFreshUntil = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyId()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyId(final String value) {
        this._topologyId = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyLastFailure()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyLastFailure(final String value) {
        this._topologyLastFailure = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyLastRefreshAttempt()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyLastRefreshAttempt(final String value) {
        this._topologyLastRefreshAttempt = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyLastSuccessfulRefresh()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyLastSuccessfulRefresh(final String value) {
        this._topologyLastSuccessfulRefresh = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyRefreshFailureCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyRefreshFailureCount(final Uint64 value) {
        this._topologyRefreshFailureCount = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyRefreshInProgress()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyRefreshInProgress(final Boolean value) {
        this._topologyRefreshInProgress = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyRefreshSuccessCount()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyRefreshSuccessCount(final Uint64 value) {
        this._topologyRefreshSuccessCount = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link ControlPlane#getTopologyTtlMillis()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public ControlPlaneBuilder setTopologyTtlMillis(final Uint64 value) {
        this._topologyTtlMillis = value;
        return this;
    }
    
    /**
      * Add an augmentation to this builder's product.
      *
      * @param augmentation augmentation to be added
      * @return this builder
      * @throws NullPointerException if {@code augmentation} is null
      */
    public ControlPlaneBuilder addAugmentation(Augmentation<ControlPlane> augmentation) {
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
    public ControlPlaneBuilder removeAugmentation(Class<? extends Augmentation<ControlPlane>> augmentationType) {
        if (this.augmentation instanceof HashMap) {
            this.augmentation.remove(augmentationType);
        }
        return this;
    }

    /**
     * A new {@link ControlPlane} instance.
     *
     * @return A new {@link ControlPlane} instance.
     */
    public @NonNull ControlPlane build() {
        return new ControlPlaneImpl(this);
    }

    private static final class ControlPlaneImpl
        extends AbstractAugmentable<ControlPlane>
        implements ControlPlane {
    
        private final Boolean _closed;
        private final Boolean _ready;
        private final Boolean _topologyFresh;
        private final String _topologyFreshUntil;
        private final String _topologyId;
        private final String _topologyLastFailure;
        private final String _topologyLastRefreshAttempt;
        private final String _topologyLastSuccessfulRefresh;
        private final Uint64 _topologyRefreshFailureCount;
        private final Boolean _topologyRefreshInProgress;
        private final Uint64 _topologyRefreshSuccessCount;
        private final Uint64 _topologyTtlMillis;
    
        ControlPlaneImpl(final ControlPlaneBuilder base) {
            super(base.augmentation);
            this._closed = base.getClosed();
            this._ready = base.getReady();
            this._topologyFresh = base.getTopologyFresh();
            this._topologyFreshUntil = base.getTopologyFreshUntil();
            this._topologyId = base.getTopologyId();
            this._topologyLastFailure = base.getTopologyLastFailure();
            this._topologyLastRefreshAttempt = base.getTopologyLastRefreshAttempt();
            this._topologyLastSuccessfulRefresh = base.getTopologyLastSuccessfulRefresh();
            this._topologyRefreshFailureCount = base.getTopologyRefreshFailureCount();
            this._topologyRefreshInProgress = base.getTopologyRefreshInProgress();
            this._topologyRefreshSuccessCount = base.getTopologyRefreshSuccessCount();
            this._topologyTtlMillis = base.getTopologyTtlMillis();
        }
    
        @Override
        public Boolean getClosed() {
            return _closed;
        }
        
        @Override
        public Boolean getReady() {
            return _ready;
        }
        
        @Override
        public Boolean getTopologyFresh() {
            return _topologyFresh;
        }
        
        @Override
        public String getTopologyFreshUntil() {
            return _topologyFreshUntil;
        }
        
        @Override
        public String getTopologyId() {
            return _topologyId;
        }
        
        @Override
        public String getTopologyLastFailure() {
            return _topologyLastFailure;
        }
        
        @Override
        public String getTopologyLastRefreshAttempt() {
            return _topologyLastRefreshAttempt;
        }
        
        @Override
        public String getTopologyLastSuccessfulRefresh() {
            return _topologyLastSuccessfulRefresh;
        }
        
        @Override
        public Uint64 getTopologyRefreshFailureCount() {
            return _topologyRefreshFailureCount;
        }
        
        @Override
        public Boolean getTopologyRefreshInProgress() {
            return _topologyRefreshInProgress;
        }
        
        @Override
        public Uint64 getTopologyRefreshSuccessCount() {
            return _topologyRefreshSuccessCount;
        }
        
        @Override
        public Uint64 getTopologyTtlMillis() {
            return _topologyTtlMillis;
        }
    
        
        
        
        
        
        
        
        
        
        
        
    
        private int hash = 0;
        private volatile boolean hashValid = false;
        
        @Override
        public int hashCode() {
            if (hashValid) {
                return hash;
            }
        
            final int result = ControlPlane.bindingHashCode(this);
            hash = result;
            hashValid = true;
            return result;
        }
    
        @Override
        public boolean equals(Object obj) {
            return ControlPlane.bindingEquals(this, obj);
        }
    
        @Override
        public String toString() {
            return ControlPlane.bindingToString(this);
        }
    }
}
