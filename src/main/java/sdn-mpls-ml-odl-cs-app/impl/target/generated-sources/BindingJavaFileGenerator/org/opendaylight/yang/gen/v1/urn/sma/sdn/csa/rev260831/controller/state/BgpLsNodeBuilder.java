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
import org.opendaylight.yangtools.binding.lib.AbstractEntryObject;
import org.opendaylight.yangtools.yang.common.Uint64;

/**
 * Class that builds {@link BgpLsNode} instances. Overall design of the class is that of a
 * <a href="https://en.wikipedia.org/wiki/Fluent_interface">fluent interface</a>, where method chaining is used.
 *
 * <p>
 * In general, this class is supposed to be used like this template:
 * <pre>
 *   <code>
 *     BgpLsNode createBgpLsNode(int fooXyzzy, int barBaz) {
 *         return new BgpLsNodeBuilder()
 *             .setFoo(new FooBuilder().setXyzzy(fooXyzzy).build())
 *             .setBar(new BarBuilder().setBaz(barBaz).build())
 *             .build();
 *     }
 *   </code>
 * </pre>
 *
 * <p>
 * This pattern is supported by the immutable nature of BgpLsNode, as instances can be freely passed around without
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
 * @see BgpLsNode
 *
 */
@Generated("mdsal-binding-generator")
public class BgpLsNodeBuilder {

    private Uint64 _graphNodeId;
    private String _nodeId;
    private String _routerId;
    private String _teRouterIdIpv4;
    private String _topologyId;
    private BgpLsNodeKey key;


    Map<Class<? extends Augmentation<BgpLsNode>>, Augmentation<BgpLsNode>> augmentation = Map.of();

    /**
     * Construct an empty builder.
     */
    public BgpLsNodeBuilder() {
        // No-op
    }

    

    /**
     * Construct a builder initialized with state from specified {@link BgpLsNode}.
     *
     * @param base BgpLsNode from which the builder should be initialized
     */
    public BgpLsNodeBuilder(final BgpLsNode base) {
        final var aug = base.augmentations();
        if (!aug.isEmpty()) {
            this.augmentation = new HashMap<>(aug);
        }
        this.key = base.key();
        this._routerId = base.getRouterId();
        this._graphNodeId = base.getGraphNodeId();
        this._nodeId = base.getNodeId();
        this._teRouterIdIpv4 = base.getTeRouterIdIpv4();
        this._topologyId = base.getTopologyId();
    }



    /**
     * Return current value associated with the property corresponding to {@link BgpLsNode#key()}.
     *
     * @return current value
     */
    public BgpLsNodeKey key() {
        return key;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link BgpLsNode#getGraphNodeId()}.
     *
     * @return current value
     */
    public Uint64 getGraphNodeId() {
        return _graphNodeId;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link BgpLsNode#getNodeId()}.
     *
     * @return current value
     */
    public String getNodeId() {
        return _nodeId;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link BgpLsNode#getRouterId()}.
     *
     * @return current value
     */
    public String getRouterId() {
        return _routerId;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link BgpLsNode#getTeRouterIdIpv4()}.
     *
     * @return current value
     */
    public String getTeRouterIdIpv4() {
        return _teRouterIdIpv4;
    }
    
    /**
     * Return current value associated with the property corresponding to {@link BgpLsNode#getTopologyId()}.
     *
     * @return current value
     */
    public String getTopologyId() {
        return _topologyId;
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
    public <E$$ extends Augmentation<BgpLsNode>> E$$ augmentation(Class<E$$> augmentationType) {
        return (E$$) augmentation.get(Objects.requireNonNull(augmentationType));
    }

    /**
     * Set the key value corresponding to {@link BgpLsNode#key()} to the specified
     * value.
     *
     * @param key desired value
     * @return this builder
     */
    public BgpLsNodeBuilder withKey(final BgpLsNodeKey key) {
        this.key = key;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link BgpLsNode#getGraphNodeId()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public BgpLsNodeBuilder setGraphNodeId(final Uint64 value) {
        this._graphNodeId = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link BgpLsNode#getNodeId()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public BgpLsNodeBuilder setNodeId(final String value) {
        this._nodeId = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link BgpLsNode#getRouterId()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public BgpLsNodeBuilder setRouterId(final String value) {
        this._routerId = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link BgpLsNode#getTeRouterIdIpv4()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public BgpLsNodeBuilder setTeRouterIdIpv4(final String value) {
        this._teRouterIdIpv4 = value;
        return this;
    }
    
    /**
     * Set the property corresponding to {@link BgpLsNode#getTopologyId()} to the specified
     * value.
     *
     * @param value desired value
     * @return this builder
     */
    public BgpLsNodeBuilder setTopologyId(final String value) {
        this._topologyId = value;
        return this;
    }
    
    /**
      * Add an augmentation to this builder's product.
      *
      * @param augmentation augmentation to be added
      * @return this builder
      * @throws NullPointerException if {@code augmentation} is null
      */
    public BgpLsNodeBuilder addAugmentation(Augmentation<BgpLsNode> augmentation) {
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
    public BgpLsNodeBuilder removeAugmentation(Class<? extends Augmentation<BgpLsNode>> augmentationType) {
        if (this.augmentation instanceof HashMap) {
            this.augmentation.remove(augmentationType);
        }
        return this;
    }

    /**
     * A new {@link BgpLsNode} instance.
     *
     * @return A new {@link BgpLsNode} instance.
     */
    public @NonNull BgpLsNode build() {
        return new BgpLsNodeImpl(this);
    }

    private static final class BgpLsNodeImpl
        extends AbstractEntryObject<BgpLsNode, BgpLsNodeKey>
        implements BgpLsNode {
    
        private final Uint64 _graphNodeId;
        private final String _nodeId;
        private final String _routerId;
        private final String _teRouterIdIpv4;
        private final String _topologyId;
    
        BgpLsNodeImpl(final BgpLsNodeBuilder base) {
            super(base.augmentation, extractKey(base));
            final var key = key();
            this._routerId = key.getRouterId();
            this._graphNodeId = base.getGraphNodeId();
            this._nodeId = base.getNodeId();
            this._teRouterIdIpv4 = base.getTeRouterIdIpv4();
            this._topologyId = base.getTopologyId();
        }
        
        private static @NonNull BgpLsNodeKey extractKey(final BgpLsNodeBuilder base) {
            final var key = base.key();
            return key != null ? key
                : new BgpLsNodeKey(base.getRouterId());
        }
    
        @Override
        public Uint64 getGraphNodeId() {
            return _graphNodeId;
        }
        
        @Override
        public String getNodeId() {
            return _nodeId;
        }
        
        @Override
        public String getRouterId() {
            return _routerId;
        }
        
        @Override
        public String getTeRouterIdIpv4() {
            return _teRouterIdIpv4;
        }
        
        @Override
        public String getTopologyId() {
            return _topologyId;
        }
    
        
        
        
        
    
        private int hash = 0;
        private volatile boolean hashValid = false;
        
        @Override
        public int hashCode() {
            if (hashValid) {
                return hash;
            }
        
            final int result = BgpLsNode.bindingHashCode(this);
            hash = result;
            hashValid = true;
            return result;
        }
    
        @Override
        public boolean equals(Object obj) {
            return BgpLsNode.bindingEquals(this, obj);
        }
    
        @Override
        public String toString() {
            return BgpLsNode.bindingToString(this);
        }
    }
}
