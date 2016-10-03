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

package org.seaborne.riot.tio ;

import static org.apache.jena.riot.tokens.TokenType.DOT ;

import java.util.ArrayList ;
import java.util.Iterator ;
import java.util.List ;
import java.util.NoSuchElementException ;

import org.apache.jena.atlas.lib.Closeable ;
import org.apache.jena.atlas.lib.tuple.Tuple ;
import org.apache.jena.riot.tokens.Token ;
import org.apache.jena.riot.tokens.Tokenizer ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

/** Handle token streams as a series of {@link Tuple}s to be iterated over. 
 *  <p>This class handles repeats.</p>. 
 */
public class TupleReader implements Iterator<Tuple<Token>>, Iterable<Tuple<Token>>, Closeable {
    private static Logger       log      = LoggerFactory.getLogger(TupleReader.class) ;
    private boolean             finished = false ;
    private final Tokenizer     tokens ;
    private Tuple<Token>        list ;
    
    public TupleReader(Tokenizer tokens) {
        this.tokens = tokens ;
    }

    @Override
    public boolean hasNext() {
        if ( finished )
            return false ;

        if ( list != null ) // Already got the reply.
            return true ;

        try {
            if ( !tokens.hasNext() ) {
                finished = true ;
                return false ;
            }
            list = buildOneLine() ;
            if ( false && log.isDebugEnabled() )
                log.debug("Tokens: " + list) ;
            if ( list == null )
                finished = true ;
            return list != null ;
        } catch (Exception ex) {
            finished = true ;
            return false ;
        }
    }

    private Tuple<Token> buildOneLine() {
        List<Token> tuple = new ArrayList<Token>() ;
        for (; tokens.hasNext();) {
            Token token = tokens.next() ;

            if ( token.hasType(DOT) ) {
                return create(tuple) ;
            }
            tuple.add(token) ;
        }

        // No final DOT
        return create(tuple) ;
    }

    @Override
    public Tuple<Token> next() {
        if ( !hasNext() )
            throw new NoSuchElementException() ;
        Tuple<Token> r = list ;
        list = null ;
        return r ;
    }

    /** Create a Tuple from a list */ 
    private /*public*/ static <X> Tuple<X> create(List<X> xs) {
        return new TupleList<X>(xs) ;
    }
    
    // --> atlas.
    static class TupleList<X> implements Tuple<X> {

        private List<X> list ;

        public TupleList(List<X> list) {
            this.list = list ;
        }
        
        @Override
        public X get(int i) {
            return list.get(i) ; 
        }

        @Override
        public int len() {
            return list.size() ;
        }
        
    }

    
    @Override
    public void remove() {
        throw new UnsupportedOperationException() ;
    }

    @Override
    public Iterator<Tuple<Token>> iterator() {
        return this ;
    }

    @Override
    public void close() {}
}
