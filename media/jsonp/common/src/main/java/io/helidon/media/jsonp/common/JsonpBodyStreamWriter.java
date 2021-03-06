/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.media.jsonp.common;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.json.JsonStructure;
import javax.json.JsonWriterFactory;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.media.jsonp.common.JsonpBodyWriter.JsonStructureToChunks;

/**
 * Message body writer for {@link javax.json.JsonStructure} sub-classes (JSON-P).
 */
public class JsonpBodyStreamWriter implements MessageBodyStreamWriter<JsonStructure> {
    private static final byte[] ARRAY_JSON_END_BYTES = "]".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ARRAY_JSON_BEGIN_BYTES = "[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMA_BYTES = ",".getBytes(StandardCharsets.UTF_8);

    private final JsonWriterFactory jsonWriterFactory;

    JsonpBodyStreamWriter(JsonWriterFactory jsonWriterFactory) {
        this.jsonWriterFactory = jsonWriterFactory;
    }

    @Override
    public boolean accept(GenericType<?> type, MessageBodyWriterContext context) {
        return JsonStructure.class.isAssignableFrom(type.rawType());
    }

    @Override
    public Multi<DataChunk> write(Publisher<? extends JsonStructure> publisher,
                                  GenericType<? extends JsonStructure> type,
                                  MessageBodyWriterContext context) {

        MediaType contentType = context.findAccepted(MediaType.JSON_PREDICATE, MediaType.APPLICATION_JSON);
        context.contentType(contentType);

        // we do not have join operator
        AtomicBoolean first = new AtomicBoolean(true);

        JsonStructureToChunks jsonToChunks = new JsonStructureToChunks(jsonWriterFactory,
                                                                       context.charset());

        return Single.just(DataChunk.create(ARRAY_JSON_BEGIN_BYTES))
                .onCompleteResumeWith(Multi.from(publisher)
                                              .map(jsonToChunks)
                                              .flatMap(it -> {
                                                  if (first.getAndSet(false)) {
                                                      // first record, do not prepend a comma
                                                      return Single.just(it);
                                                  } else {
                                                      // any subsequent record starts with a comma
                                                      return Multi.just(DataChunk.create(COMMA_BYTES), it);
                                                  }
                                              }))
                .onCompleteResume(DataChunk.create(ARRAY_JSON_END_BYTES));
    }
}
