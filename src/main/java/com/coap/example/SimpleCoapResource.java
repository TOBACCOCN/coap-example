package com.coap.example;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveNotificationOrderer;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObserveRelationContainer;
import org.eclipse.californium.core.observe.ObserveRelationFilter;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.core.server.resources.ResourceAttributes;
import org.eclipse.californium.core.server.resources.ResourceObserver;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.eclipse.californium.core.coap.CoAP.ResponseCode;
import static org.eclipse.californium.core.coap.CoAP.Type;

public class SimpleCoapResource extends CoapResource {

    protected static final Logger LOGGER = Logger.getLogger(CoapResource.class.getCanonicalName());
    private final ResourceAttributes attributes;
    private final ReentrantLock recursionProtection;
    private String name;
    private String path;
    private boolean visible;
    private boolean observable;
    private ConcurrentHashMap<String, Resource> children;
    private Resource parent;
    private Type observeType;
    private List<ResourceObserver> observers;
    private ObserveRelationContainer observeRelations;
    private ObserveNotificationOrderer notificationOrderer;

    public SimpleCoapResource(String name) {
        this(name, true);
    }

    public SimpleCoapResource(String name, boolean visible) {
        super(name, visible);
        this.recursionProtection = new ReentrantLock();
        this.observeType = null;
        this.name = name;
        this.path = "";
        this.visible = visible;
        this.attributes = new ResourceAttributes();
        this.children = new ConcurrentHashMap<>();
        this.observers = new CopyOnWriteArrayList<>();
        this.observeRelations = new ObserveRelationContainer();
        this.notificationOrderer = new ObserveNotificationOrderer();
    }

    public void handleRequest(Exchange exchange) {
        CoAP.Code code = exchange.getRequest().getCode();
        switch(code) {
            case GET:
                this.handleGET(new CoapExchange(exchange, this));
                break;
            case POST:
                this.handlePOST(new CoapExchange(exchange, this));
                break;
            case PUT:
                this.handlePUT(new CoapExchange(exchange, this));
                break;
            case DELETE:
                this.handleDELETE(new CoapExchange(exchange, this));
                break;
            case FETCH:
                this.handleFETCH(new CoapExchange(exchange, this));
                break;
            case PATCH:
                this.handlePATCH(new CoapExchange(exchange, this));
                break;
            case IPATCH:
                this.handleIPATCH(new CoapExchange(exchange, this));
        }
    }

    public void handleGET(CoapExchange exchange) {
        exchange.respond(ResponseCode.METHOD_NOT_ALLOWED);
    }

    public void handlePOST(CoapExchange exchange) {
        exchange.respond(ResponseCode.METHOD_NOT_ALLOWED);
    }

    public void handlePUT(CoapExchange exchange) {
        exchange.respond(ResponseCode.METHOD_NOT_ALLOWED);
    }

    public void handleDELETE(CoapExchange exchange) {
        exchange.respond(ResponseCode.METHOD_NOT_ALLOWED);
    }

    public void handleFETCH(CoapExchange exchange) {
        exchange.respond(ResponseCode.METHOD_NOT_ALLOWED);
    }

    public void handlePATCH(CoapExchange exchange) {
        exchange.respond(ResponseCode.METHOD_NOT_ALLOWED);
    }

    public void handleIPATCH(CoapExchange exchange) {
        exchange.respond(ResponseCode.METHOD_NOT_ALLOWED);
    }

    public void checkObserveRelation(Exchange exchange, Response response) {
        ObserveRelation relation = exchange.getRelation();
        if (relation != null && !relation.isCanceled()) {
            if (ResponseCode.isSuccess(response.getCode())) {
                response.getOptions().setObserve(this.notificationOrderer.getCurrent());
                if (!relation.isEstablished()) {
                    relation.setEstablished();
                    this.addObserveRelation(relation);
                } else if (this.observeType != null) {
                    response.setType(this.observeType);
                }
            }

        }
    }

    public CoapClient createClient() {
        CoapClient client = new CoapClient();
        client.setExecutors(this.getExecutor(), this.getSecondaryExecutor(), false);
        List<Endpoint> endpoints = this.getEndpoints();
        if (!endpoints.isEmpty()) {
            client.setEndpoint(endpoints.get(0));
        }

        return client;
    }

    public CoapClient createClient(URI uri) {
        return this.createClient().setURI(uri.toString());
    }

    public CoapClient createClient(String uri) {
        return this.createClient().setURI(uri);
    }

    // public synchronized void add(Resource child) {
    //     if (child.getName() == null) {
    //         throw new NullPointerException("Child must have a name");
    //     } else {
    //         if (child.getParent() != null) {
    //             child.getParent().delete(child);
    //         }
    //
    //         this.children.put(child.getName(), child);
    //         child.setParent(this);
    //
    //         this.observers.forEach(obs -> obs.addedChild(child));
    //
    //     }
    // }

    // --------------------------------- 自定义 add 方法 ---------------------------------
    public synchronized void add(Resource child) {
        String childName = child.getName();
        if (childName == null) {
            throw new NullPointerException("Child must have a name");
        } else {
            if (child.getParent() != null) {
                child.getParent().delete(child);
            }

            String name = this.getName();
            if (name.equals(childName)) {
                traverse(child);
            } else {
                this.children.put(childName, child);
            }
            child.setParent(this);

            this.observers.forEach(obs ->obs.addedChild(child));
        }
    }

    private void traverse(Resource child) {
        List<String> existChildNames = new ArrayList<>();
        this.getChildren().forEach(existChild -> existChildNames.add(existChild.getName()));
        child.getChildren().forEach(childOfChild -> {
            String childOfChildName = childOfChild.getName();
            if (existChildNames.contains(childOfChildName)) {
                traverse(childOfChild);
            } else {
                this.children.put(childOfChildName, childOfChild);
            }
        });
    }

    public synchronized SimpleCoapResource add(CoapResource child) {
        this.add((Resource) child);
        return this;
    }

    public synchronized SimpleCoapResource add(CoapResource... children) {
        Stream.of(children).forEach(this::add);
        return this;
    }
    // --------------------------------- 自定义 add 方法 ---------------------------------

    public synchronized boolean delete(Resource child) {
        Resource deleted = this.delete(child.getName());
        if (deleted != child) {
            return false;
        } else {
            child.setParent(null);
            child.setPath(null);

            this.observers.forEach(obs -> obs.removedChild(child));

            return true;
        }
    }

    public synchronized Resource delete(String name) {
        return this.children.remove(name);
    }

    public synchronized void delete() {
        Resource parent = this.getParent();
        if (parent != null) {
            parent.delete(this);
        }

        if (this.isObservable()) {
            this.clearAndNotifyObserveRelations(ResponseCode.NOT_FOUND);
        }

    }

    public void clearAndNotifyObserveRelations(ResponseCode code) {
        for (ObserveRelation relation : this.observeRelations) {
            relation.cancel();
            relation.getExchange().sendResponse(new Response(code));
        }
    }

    public void clearObserveRelations() {
        this.observeRelations.forEach(ObserveRelation::cancel);
    }

    public Resource getParent() {
        return this.parent;
    }

    public void setParent(Resource parent) {
        this.parent = parent;
        if (parent != null) {
            this.path = parent.getPath() + parent.getName() + "/";
        }

        this.adjustChildrenPath();
    }

    public Resource getChild(String name) {
        return this.children.get(name);
    }

    public synchronized void addObserver(ResourceObserver observer) {
        this.observers.add(observer);
    }

    public synchronized void removeObserver(ResourceObserver observer) {
        this.observers.remove(observer);
    }

    public ResourceAttributes getAttributes() {
        return this.attributes;
    }

    public String getName() {
        return this.name;
    }

    public boolean isCachable() {
        return true;
    }

    public String getPath() {
        return this.path;
    }

    public String getURI() {
        return this.getPath() + this.getName();
    }

    public synchronized void setPath(String path) {
        String old = this.path;
        this.path = path;

        this.observers.forEach(obs -> obs.changedPath(old));

        this.adjustChildrenPath();
    }

    public synchronized void setName(String name) {
        if (name == null) {
            throw new NullPointerException();
        } else {
            String old = this.name;
            Resource parent = this.getParent();
            if (parent != null) {
                synchronized (parent) {
                    parent.delete(this);
                    this.name = name;
                    parent.add(this);
                }
            } else {
                this.name = name;
            }

            this.adjustChildrenPath();

            this.observers.forEach(obs -> obs.changedName(old));
        }
    }

    private void adjustChildrenPath() {
        String childpath = this.path + this.name + "/";

        for (Resource child : this.children.values()) {
            child.setPath(childpath);
        }

    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isObservable() {
        return this.observable;
    }

    public void setObservable(boolean observable) {
        this.observable = observable;
    }

    public void setObserveType(CoAP.Type type) {
        if (type != Type.ACK && type != Type.RST) {
            this.observeType = type;
        } else {
            throw new IllegalArgumentException("Only CON and NON notifications are allowed or null for no changes by the framework");
        }
    }

    public void addObserveRelation(ObserveRelation relation) {
        if (this.observeRelations.add(relation)) {
            LOGGER.log(Level.INFO, "Replacing observe relation between {0} and resource {1}", new Object[]{relation.getKey(), this.getURI()});
        } else {
            LOGGER.log(Level.INFO, "Successfully established observe relation between {0} and resource {1}", new Object[]{relation.getKey(), this.getURI()});
        }

        this.observers.forEach(obs -> obs.removedObserveRelation(relation));

    }

    public void removeObserveRelation(ObserveRelation relation) {
        this.observeRelations.remove(relation);
        this.observers.forEach(obs -> obs.removedObserveRelation(relation));
    }

    public int getObserverCount() {
        return this.observeRelations.getSize();
    }

    public void changed() {
        this.changed(null);
    }

    public void changed(final ObserveRelationFilter filter) {
        Executor executor = this.getExecutor();
        if (executor == null) {
            if (this.recursionProtection.isHeldByCurrentThread()) {
                throw new IllegalStateException("Recursion detected! Please call \"changed()\" using an executor.");
            }

            this.recursionProtection.lock();

            try {
                this.notifyObserverRelations(filter);
            } finally {
                this.recursionProtection.unlock();
            }
        } else {
            executor.execute(() -> SimpleCoapResource.this.notifyObserverRelations(filter));
        }

    }

    protected void notifyObserverRelations(ObserveRelationFilter filter) {
        this.notificationOrderer.getNextObserveNumber();
        Iterator i$ = this.observeRelations.iterator();

        while (true) {
            ObserveRelation relation;
            do {
                if (!i$.hasNext()) {
                    return;
                }

                relation = (ObserveRelation) i$.next();
            } while (null != filter && !filter.accept(relation));

            relation.notifyObservers();
        }
    }

    public Collection<Resource> getChildren() {
        return this.children.values();
    }

    public ExecutorService getExecutor() {
        return this.parent != null ? this.parent.getExecutor() : null;
    }

    public void execute(Runnable task) {
        Executor executor = this.getExecutor();
        if (executor == null) {
            task.run();
        } else {
            executor.execute(task);
        }

    }

    public void executeAndWait(final Runnable task) throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);
        this.execute(() -> {
            task.run();
            semaphore.release();
        });
        semaphore.acquire();
    }

    public List<Endpoint> getEndpoints() {
        return this.parent == null ? Collections.emptyList() : this.parent.getEndpoints();
    }

}
