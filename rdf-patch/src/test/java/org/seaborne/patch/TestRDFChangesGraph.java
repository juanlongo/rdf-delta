/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seaborne.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.atlas.lib.ListUtils;
import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.TransactionHandler;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.TransactionHandlerBase;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.JenaException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.graph.GraphWrapper;
import org.apache.jena.sparql.sse.SSE;
import org.junit.Test;
import org.seaborne.patch.changes.RDFChangesWriter;
import org.seaborne.patch.system.GraphChanges;
import org.seaborne.riot.tio.TokenWriter;
import org.seaborne.riot.tio.impl.TokenWriterText;

// FIXME
// When Jena 3.6. is available, upgrade and remove 

public class TestRDFChangesGraph {
    // ---- FIXME XXX Need a transactional graph! Jena 3.6.0
    // Fakery needed! (until graph transactions in Jena are fully integrated)
    private static Graph txnGraph() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        Graph g = dsg.getDefaultGraph();
        g = new GraphWrapper(g) {
            @Override public TransactionHandler getTransactionHandler() { return new TransactionHandlerView(dsg); }
        };
        return g;
    }
    private static Graph txnGraph(String graphName) {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        Node gn = NodeFactory.createURI(graphName);
        Graph g = dsg.getGraph(gn);
        g = new GraphWrapper(g) {
            @Override public TransactionHandler getTransactionHandler() { return new TransactionHandlerView(dsg); }
        };
        return g;
    }
    // ---- FIXME

//    Graph graphBase = txnGraph(DatasetGraphFactory.createTxnMem());
//    // Graph with changes.
//    Graph graph = changesAsText(graphBase, bout);
    
    private static Triple triple1 = SSE.parseTriple("(_:sx <p1> 11)");
    private static Triple triple2 = SSE.parseTriple("(_:sx <p2> 22)");
    
    // -- RDFPatchOps
    public static Graph changesGraph(Graph graph, OutputStream out) {
        TokenWriter tokenWriter = new TokenWriterText(out);
        RDFChanges changeLog = new RDFChangesWriter(tokenWriter);
        return changesGraph(graph, changeLog);
    }
    
    public static Graph changesGraph(Graph graph, RDFChanges changes) {
        return new GraphChanges(graph, changes);
    }
    
    // Bytes for changes
    private ByteArrayOutputStream bout;
    // The underlying graph
    private Graph baseGraph;
    // The graph with changes wrapper. 
    private Graph graph;
    
    // ----
    // Replay a chnages byte stream into a completely fresh graph 
    private Graph replay() {
        IO.close(bout);
        final boolean DEBUG = false;
        
        if ( DEBUG ) {
            System.out.println("== Graph ==");
            RDFDataMgr.write(System.out, baseGraph, Lang.NQ);
            System.out.println("== Replay ==");
            String x = StrUtils.fromUTF8bytes(bout.toByteArray());
            System.out.print(x);
            System.out.println("== ==");
        }
        
        // A completely separate graph (different dataset)
        Graph graph2 = txnGraph();
        
        try(ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray())) {
            RDFPatchOps.applyChange(graph2, bin);
            if ( DEBUG ) {
                System.out.println("== Graph outcome ==");
                RDFDataMgr.write(System.out, graph2, Lang.NT);
                System.out.println("== ==");
            }
            return graph2;
        } catch (IOException ex) { IO.exception(ex); return null; }
    }
    
    private static void check(Graph graph, Triple...quads) {
        if ( quads.length == 0 ) {
            assertTrue(graph.isEmpty());
            return;
        }
        List<Triple> listExpected = Arrays.asList(quads); 
        List<Triple> listActual = Iter.toList(graph.find());
        assertEquals(listActual.size(), listExpected.size());
        assertTrue(ListUtils.equalsUnordered(listExpected, listActual));
    }
    
    private static void txn(Graph graph, Runnable action) {
        graph.getTransactionHandler().execute(action);
    }
    
    // ----
    
    private void setup() {
        this.bout = new ByteArrayOutputStream();
        this.baseGraph = txnGraph();
        this.graph = changesGraph(baseGraph, bout);
    }
    
    private void setup(String graphName) {
        this.bout = new ByteArrayOutputStream();
        this.baseGraph = txnGraph(graphName);
        this.graph = changesGraph(baseGraph, bout);
    }
    
    @Test public void record_00() {
        setup();
        
        Graph graph2 = replay();
        check(graph2);
    }
    
    @Test public void record_add() {
        setup();
        
        txn(graph, ()->graph.add(triple1));
        Graph g2 = replay();
        check(g2, triple1);
    }
    
    @Test public void record_add_add_1() {
        setup();
        
        txn(graph, ()-> {
            graph.add(triple1);
            graph.add(triple2);
        });
        Graph g2 = replay();
        check(g2, triple1, triple2);
    }


    @Test public void record_add_delete_1() {
        setup();

        txn(graph, ()-> {
            graph.add(triple1);
            graph.delete(triple1);
        });
        Graph g2 = replay();
        check(g2);
    }

    
    @Test public void record_add_abort_2() {
        setup();

        TransactionHandler h = graph.getTransactionHandler();
        h.begin();
        graph.add(triple1);
        h.abort();
        Graph g2 = replay();
        check(g2);
    }
    
    @Test public void record_add_abort_1() {
        setup();
        try {
            txn(graph, ()->{
                graph.add(triple1);
                throw new JenaException("Abort!");
            });
        } catch (JenaException ex) {}
        Graph g2 = replay();
        check(g2);
    }
    
    @Test public void record_named_graph_1() {
        setup("http://example/graph");
        txn(graph, ()->graph.add(triple1));
        Graph g2 = replay();
        check(g2, triple1);
    }
        
    static class TransactionHandlerView extends TransactionHandlerBase 
    {
        private final DatasetGraph dsg;

        public TransactionHandlerView(DatasetGraph dsg) {
            this.dsg = dsg;
        }

        protected DatasetGraph getDSG() { return dsg; }    

        @Override
        public void abort() {
            getDSG().abort();
            getDSG().end();
        }

        @Override
        public void begin() {
            if ( false /* dsg.supportPromotion */)
                getDSG().begin(ReadWrite.READ);
            else
                getDSG().begin(ReadWrite.WRITE);
        }

        @Override
        public void commit() {
            getDSG().commit();
            getDSG().end();
        }

        @Override
        public boolean transactionsSupported() {
            // Abort required.
            return getDSG().supportsTransactionAbort();
        }
    }
}