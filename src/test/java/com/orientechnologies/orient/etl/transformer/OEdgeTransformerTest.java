/*
 *
 *  * Copyright 2010-2014 Orient Technologies LTD (info(at)orientechnologies.com)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.orientechnologies.orient.etl.transformer;

import com.orientechnologies.orient.etl.ETLBaseTest;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import org.junit.Test;

import java.util.Iterator;
import java.util.Set;

/**
 * Tests ETL Field Transformer.
 *
 * @author Luca Garulli
 */
public class OEdgeTransformerTest extends ETLBaseTest {

  @Override
  public void setUp() {
    super.setUp();
    graph.createVertexType("V1");
    graph.createVertexType("V2");
    graph.createEdgeType("Friend");

    graph.addVertex("class:V2").setProperty("name", "Luca");
    graph.commit();
  }

  @Test
  public void testNotLightweightEdge() {
    process("{source: { content: { value: 'name,surname,friend\nJay,Miner,Luca' } }, extractor : { row: {} },"
        + " transformers: [{csv: {}}, {vertex: {class:'V1'}}, {edge:{class:'Friend',joinFieldName:'friend',lookup:'V2.name'}},"
        + "], loader: { orientdb: { dbURL: 'memory:ETLBaseTest', dbType:'graph', useLightweightEdges:false } } }");

    assertEquals(1, graph.countVertices("V1"));
    assertEquals(1, graph.countVertices("V2"));
    assertEquals(1, graph.countEdges("Friend"));
  }

  @Test
  public void testEdgeWithProperties() {
    process("{source: { content: { value: 'id,name,surname,friendSince,friendId,friendName,friendSurname\n0,Jay,Miner,1996,1,Luca,Garulli' } }, extractor : { row: {} },"
        + " transformers: [{csv: {}}, {vertex: {class:'V1'}}, "
        + "{edge:{unresolvedLinkAction:'CREATE',class:'Friend',joinFieldName:'friendId',lookup:'V2.fid',targetVertexFields:{name:'${input.friendName}',surname:'${input.friendSurname}'},edgeFields:{since:'${input.friendSince}'}}},"
        + "{field:{fieldNames:['friendSince','friendId','friendName','friendSurname'],operation:'remove'}}"
        + "], loader: { orientdb: { dbURL: 'memory:ETLBaseTest', dbType:'graph', useLightweightEdges:false } } }");

    assertEquals(1, graph.countVertices("V1"));
    assertEquals(2, graph.countVertices("V2"));
    assertEquals(1, graph.countEdges("Friend"));

    final Iterator<Vertex> v = graph.getVerticesOfClass("V2").iterator();
    assertTrue(v.hasNext());
    assertNotNull(v.next());
    assertTrue(v.hasNext());
    final Vertex v1 = v.next();
    assertNotNull(v1);

    final Set<String> v1Props = v1.getPropertyKeys();

    assertEquals(3, v1Props.size());
    assertEquals(v1.getProperty("name"), "Luca");
    assertEquals(v1.getProperty("surname"), "Garulli");
    assertEquals(v1.getProperty("fid"), 1);

    final Iterator<Edge> edge = v1.getEdges(Direction.IN).iterator();
    assertTrue(edge.hasNext());

    final Edge e = edge.next();
    assertNotNull(e);
    final Set<String> eProps = e.getPropertyKeys();
    assertEquals(1, eProps.size());
    assertEquals(e.getProperty("since"), 1996);

    final Vertex v0 = e.getVertex(Direction.OUT);
    assertNotNull(v0);

    final Set<String> v0Props = v0.getPropertyKeys();

    assertEquals(3, v0Props.size());
    assertEquals(v0.getProperty("name"), "Jay");
    assertEquals(v0.getProperty("surname"), "Miner");
    assertEquals(v0.getProperty("id"), 0);
  }

  @Test
  public void testEdgeSkipDuplicatesTrue() {
    process("{config:{log:'debug'},source: { content: { value: 'id,vertexId,links\n0,1,2\n1,2,1\n2,1,2' } }, extractor : { row: {} },"
        + " transformers: [{csv: {}}, "
        + "{merge: {joinFieldName: 'vertexId', lookup: 'VertexWithOneEdge.vertexId'}},"
        + "{vertex: {class:'VertexWithOneEdge'}}, "
        + "{edge:{unresolvedVertexAction:'CREATE',unresolvedLinkAction:'CREATE',class:'EdgeWithIndex',joinFieldName:'links',lookup:'VertexWithOneEdge.vertexId',"
        + " skipDuplicates:true}},"
        + "{field:{ fieldName:'links',operation:'remove'}}"
        + "], loader: { orientdb: { dbURL: 'memory:ETLBaseTest', dbType:'graph', classes: ["
        + "{name: 'VertexWithOneEdge', extends: 'V'},{name: 'EdgeWithIndex', extends: 'E'}"
        + "],indexes: ["
        + "{class: 'VertexWithOneEdge', fields:['vertexId:string'], type: 'UNIQUE_HASH_INDEX'},"
        + "{class: 'EdgeWithIndex', fields:['in:LINK','out:LINK'], type: 'UNIQUE_HASH_INDEX'}"
        + "]}}}");

    assertEquals(2, graph.countVertices("VertexWithOneEdge"));
    final Iterator<Vertex> v = graph.getVerticesOfClass("VertexWithOneEdge").iterator();
    assertTrue(v.hasNext());
    final Vertex v1 = v.next();
    assertNotNull(v1);
    final Vertex v2 = v.next();
    assertNotNull(v2);

    //Would be 3 without the in/out index and skipDuplicates:false
    assertEquals(2, graph.countEdges("EdgeWithIndex"));

  }

  @Test
  public void testEdgeSkipDuplicatesFalse() {
    process("{config:{log:'debug'},source: { content: { value: 'id,vertexId,links\n0,1,2\n1,2,1\n2,1,2' } }, extractor : { row: {} },"
        + " transformers: [{csv: {}}, "
        + "{merge: {joinFieldName: 'vertexId', lookup: 'VertexWithOneEdge.vertexId'}},"
        + "{vertex: {class:'VertexWithOneEdge'}}, "
        + "{edge:{unresolvedVertexAction:'CREATE',unresolvedLinkAction:'CREATE',class:'EdgeWithIndex',joinFieldName:'links',lookup:'VertexWithOneEdge.vertexId',"
        + " skipDuplicates:false}},"
        + "{field:{ fieldName:'links',operation:'remove'}}"
        + "], loader: { orientdb: { dbURL: 'memory:ETLBaseTest', dbType:'graph', classes: ["
        + "{name: 'VertexWithOneEdge', extends: 'V'},{name: 'EdgeWithIndex', extends: 'E'}"
        + "],indexes: ["
        + "{class: 'VertexWithOneEdge', fields:['vertexId:string'], type: 'UNIQUE_HASH_INDEX'}"
        + "]}}}");

    assertEquals(2, graph.countVertices("VertexWithOneEdge"));
    final Iterator<Vertex> v = graph.getVerticesOfClass("VertexWithOneEdge").iterator();
    assertTrue(v.hasNext());
    final Vertex v1 = v.next();
    assertNotNull(v1);
    final Vertex v2 = v.next();
    assertNotNull(v2);

    assertEquals(3, graph.countEdges("EdgeWithIndex"));

  }


}
