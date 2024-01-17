/*
 * Copyright 2019 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.server.file;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.annotation.Nullable;
import com.linecorp.armeria.common.annotation.UnstableApi;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.file.HttpFileBuilder.ClassPathHttpFileBuilder;
import com.linecorp.armeria.server.file.HttpFileBuilder.FileSystemHttpFileBuilder;
import com.linecorp.armeria.server.file.HttpFileBuilder.HttpDataFileBuilder;
import com.linecorp.armeria.server.file.HttpFileBuilder.NonExistentHttpFileBuilder;
import com.linecorp.armeria.unsafe.PooledObjects;

import io.netty.buffer.ByteBufAllocator;

/**
 * A file-like HTTP resource which yields an {@link HttpResponse}.
 * <pre>{@code
 * HttpFile faviconFile = HttpFile.of(new File("/var/www/favicon.ico"));
 * ServerBuilder builder = Server.builder();
 * builder.service("/favicon.ico", faviconFile.asService());
 * Server server = builder.build();
 * }</pre>
 *
 * @see HttpFileBuilder
 */
public interface HttpFile {

    /**
     * Creates a new {@link HttpFile} which streams the specified {@link File}.
     */
    static HttpFile of(File file) {
        return builder(file).build();
    }

    /**
     * Creates a new {@link HttpFile} which streams the file at the specified {@link Path}.
     */
    static HttpFile of(Path path) {
        return builder(path).build();
    }

    /**
     * Creates a new {@link HttpFile} which streams the resource at the specified {@code path}, loaded by
     * the specified {@link ClassLoader}.
     *
     * @param classLoader the {@link ClassLoader} which will load the resource at the {@code path}
     * @param path the path to the resource
     */
    static HttpFile of(ClassLoader classLoader, String path) {
        return builder(classLoader, path).build();
    }

    /**
     * Creates a new {@link HttpFile} which streams the specified {@link HttpData}. This method is
     * a shortcut for {@code HttpFile.of(data, System.currentTimeMillis()}.
     */
    static HttpFile of(HttpData data) {
        return builder(data).build();
    }

    /**
     * Creates a new {@link HttpFile} which streams the specified {@link HttpData} with the specified
     * {@code lastModifiedMillis}.
     *
     * @param data the data that provides the content of an HTTP response
     * @param lastModifiedMillis when the {@code data} has been last modified, represented as the number of
     *                           millisecond since the epoch
     */
    static HttpFile of(HttpData data, long lastModifiedMillis) {
        return builder(data, lastModifiedMillis).build();
    }

    /**
     * Creates a new {@link HttpFile} which caches the content and attributes of the specified {@link HttpFile}.
     * The cache is automatically invalidated when the {@link HttpFile} is updated.
     *
     * @param file the {@link HttpFile} to cache
     * @param maxCachingLength the maximum allowed length of the {@link HttpFile} to cache. if the length of
     *                         the {@link HttpFile} exceeds this value, no caching will be performed.
     */
    static HttpFile ofCached(HttpFile file, int maxCachingLength) {
        requireNonNull(file, "file");
        if (maxCachingLength<0){
          return null;
        }
        if (maxCachingLength == 0) {
            return file;
        } else {
            return new CachingHttpFile(file, maxCachingLength);
        }
    }

    /**
     * Returns an {@link HttpFile} which represents a non-existent file.
     */
    static HttpFile nonExistent() {
        return NonExistentHttpFile.INSTANCE;
    }

    /**
     * Returns an {@link HttpFile} redirected to the specified {@code location}.
     */
    static HttpFile ofRedirect(String location) {
        requireNonNull(location, "location");
        return new NonExistentHttpFile(location);
    }

    /**
     * Returns an {@link HttpFile} that becomes readable when the specified {@link CompletionStage} is complete.
     * All {@link HttpFile} operations will wait until the specified {@link CompletionStage} is completed.
     */
    static HttpFile from(CompletionStage<? extends HttpFile> stage) {
        return new DeferredHttpFile(requireNonNull(stage, "stage"));
    }

    /**
     * Returns a new {@link HttpFileBuilder} that builds an {@link HttpFile} from the file at the specified
     * {@link File}.
     */
    static HttpFileBuilder builder(File file) {
        return builder(requireNonNull(file, "file").toPath());
    }

    /**
     * Returns a new {@link HttpFileBuilder} that builds an {@link HttpFile} from the file at the specified
     * {@link Path}.
     */
    static HttpFileBuilder builder(Path path) {
        return new FileSystemHttpFileBuilder(requireNonNull(path, "path"));
    }

    /**
     * Returns a new {@link HttpFileBuilder} that builds an {@link HttpFile} from the specified
     * {@link HttpData}. The last modified date of the file is set to 'now'.
     */
    static HttpFileBuilder builder(HttpData data) {
        return builder(data, System.currentTimeMillis());
    }

    /**
     * Returns a new {@link HttpFileBuilder} that builds an {@link HttpFile} from the specified
     * {@link HttpData} and {@code lastModifiedMillis}.
     *
     * @param data the content of the file
     * @param lastModifiedMillis the last modified time represented as the number of milliseconds
     *                           since the epoch
     */
    static HttpFileBuilder builder(HttpData data, long lastModifiedMillis) {
        requireNonNull(data, "data");
        return new HttpDataFileBuilder(data, lastModifiedMillis)
                .autoDetectedContentType(false); // Can't auto-detect because there's no path or URI.
    }

    /**
     * Returns a new {@link HttpFileBuilder} that builds an {@link HttpFile} from the specified
     * {@link URL}. {@code file:}, {@code jrt:} and {@linkplain JarURLConnection jar:file:} protocol.
     */
    static HttpFileBuilder builder(URL url) {
        requireNonNull(url, "url");
        if (url.getPath().endsWith("/")) {
            // Non-existent resource.
            return new NonExistentHttpFileBuilder();
        }

        // Convert to a real file if possible.
        if ("file".equals(url.getProtocol())) {
            File f;
            try {
                f = new File(url.toURI());
            } catch (URISyntaxException ignored) {
                f = new File(url.getPath());
            }

            return builder(f.toPath());
        } else if ("jar".equals(url.getProtocol()) && (url.getPath().startsWith("file:")||url.getPath().startsWith("nested:")) ||
                   "jrt".equals(url.getProtocol()) ||
                   "bundle".equals(url.getProtocol())) {
            return new ClassPathHttpFileBuilder(url);
        }
        throw new IllegalArgumentException("Unsupported URL: " + url + " (must start with " +
                                           "'file:', 'jar:file', 'jrt:' or 'bundle:')");
    }

    /**
     * Returns a new {@link HttpFileBuilder} that builds an {@link HttpFile} from the classpath resource
     * at the specified {@code path} using the specified {@link ClassLoader}.
     */
    static HttpFileBuilder builder(ClassLoader classLoader, String path) {
        requireNonNull(classLoader, "classLoader");
        requireNonNull(path, "path");

        // Strip the leading slash.
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Retrieve the resource URL.
        @Nullable
        final URL url = classLoader.getResource(path);
        if (url == null) {
            // Non-existent resource.
            return new NonExistentHttpFileBuilder();
        }
        return builder(url);
    }

    /**
     * Retrieves the attributes of this file.
     *
     * @param fileReadExecutor the {@link Executor} which will perform the read operations against the file
     *
     * @return the {@link CompletableFuture} that will be completed with the attributes of this file.
     *         It will be completed with {@code null} if the file does not exist.
     */
    CompletableFuture<@Nullable HttpFileAttributes> readAttributes(Executor fileReadExecutor);

    /**
     * Reads the attributes of this file as {@link ResponseHeaders}, which could be useful for building
     * a response for a {@code HEAD} request.
     *
     * @param fileReadExecutor the {@link Executor} which will perform the read operations against the file
     *
     * @return the {@link CompletableFuture} that will be completed with the headers.
     *         It will be completed with {@code null} if the file does not exist.
     */
    CompletableFuture<@Nullable ResponseHeaders> readHeaders(Executor fileReadExecutor);

    /**
     * Starts to stream this file into the returned {@link HttpResponse}.
     *
     * @param fileReadExecutor the {@link Executor} which will perform the read operations against the file
     * @param alloc the {@link ByteBufAllocator} which will allocate the buffers that hold the content of
     *              the file
     * @return the {@link CompletableFuture} that will be completed with the response.
     *         It will be completed with {@code null} if the file does not exist.
     */
    CompletableFuture<@Nullable HttpResponse> read(Executor fileReadExecutor, ByteBufAllocator alloc);

    /**
     * Converts this file into an {@link AggregatedHttpFile}.
     *
     * @param fileReadExecutor the {@link Executor} which will perform the read operations against the file
     *
     * @return a {@link CompletableFuture} which will complete when the aggregation process is finished, or
     *         a {@link CompletableFuture} successfully completed with {@code this}, if this file is already
     *         an {@link AggregatedHttpFile}.
     */
    CompletableFuture<AggregatedHttpFile> aggregate(Executor fileReadExecutor);

    /**
     * (Advanced users only) Converts this file into an {@link AggregatedHttpFile}.
     * {@link AggregatedHttpFile#content()} will return a pooled {@link HttpData}, and the caller must
     * ensure to release it. If you don't know what this means, use {@link #aggregate(Executor)}.
     *
     * @param fileReadExecutor the {@link Executor} which will perform the read operations against the file
     * @param alloc the {@link ByteBufAllocator} which will allocate the content buffer
     *
     * @return a {@link CompletableFuture} which will complete when the aggregation process is finished, or
     *         a {@link CompletableFuture} successfully completed with {@code this}, if this file is already
     *         an {@link AggregatedHttpFile}.
     *
     * @see PooledObjects
     */
    @UnstableApi
    CompletableFuture<AggregatedHttpFile> aggregateWithPooledObjects(Executor fileReadExecutor,
                                                                     ByteBufAllocator alloc);

    /**
     * Returns an {@link HttpService} which serves the file for {@code HEAD} and {@code GET} requests.
     */
    HttpService asService();
}
