package io.jenkins.plugins.appcenter.task.internal;

import hudson.model.TaskListener;
import io.jenkins.plugins.appcenter.AppCenterException;
import io.jenkins.plugins.appcenter.AppCenterLogger;
import io.jenkins.plugins.appcenter.api.AppCenterServiceFactory;
import io.jenkins.plugins.appcenter.model.appcenter.ReleaseUploadBeginRequest;
import io.jenkins.plugins.appcenter.model.appcenter.SymbolUploadBeginRequest;
import io.jenkins.plugins.appcenter.task.request.UploadRequest;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

@Singleton
public final class CreateUploadResourceTask implements AppCenterTask<UploadRequest>, AppCenterLogger {

    private static final long serialVersionUID = 1L;

    @Nonnull
    private final TaskListener taskListener;
    @Nonnull
    private final AppCenterServiceFactory factory;

    @Inject
    CreateUploadResourceTask(@Nonnull final TaskListener taskListener,
                             @Nonnull final AppCenterServiceFactory factory) {
        this.taskListener = taskListener;
        this.factory = factory;
    }

    @Nonnull
    @Override
    public CompletableFuture<UploadRequest> execute(@Nonnull UploadRequest request) {
        if (request.symbolUploadRequest == null) {
            return createUploadResourceForApp(request);
        } else {
            return createUploadResourceForApp(request)
                .thenCompose(this::createUploadResourceForDebugSymbols);
        }
    }

    @Nonnull
    private CompletableFuture<UploadRequest> createUploadResourceForApp(@Nonnull UploadRequest request) {
        log("Creating an upload resource for app.");

        final CompletableFuture<UploadRequest> future = new CompletableFuture<>();

        final ReleaseUploadBeginRequest releaseUploadBeginRequest = new ReleaseUploadBeginRequest(request.buildVersion, null);
        factory.createAppCenterService()
            .releaseUploadsCreate(request.ownerName, request.appName, releaseUploadBeginRequest)
            .whenComplete((releaseUploadBeginResponse, throwable) -> {
                if (throwable != null) {
                    final AppCenterException exception = logFailure("Create upload resource for app unsuccessful", throwable);
                    future.completeExceptionally(exception);
                } else {
                    log("Create upload resource for app successful.");
                    final UploadRequest uploadRequest = request.newBuilder()
                        .setUploadId(releaseUploadBeginResponse.id)
                        .setUploadDomain(releaseUploadBeginResponse.upload_domain)
                        .setToken(releaseUploadBeginResponse.url_encoded_token)
                        .setPackageAssetId(releaseUploadBeginResponse.package_asset_id)
                        .build();
                    future.complete(uploadRequest);
                }
            });

        return future;
    }

    @Nonnull
    private CompletableFuture<UploadRequest> createUploadResourceForDebugSymbols(@Nonnull UploadRequest request) {
        final SymbolUploadBeginRequest symbolUploadRequest = requireNonNull(request.symbolUploadRequest, "symbolUploadRequest cannot be null");

        log("Creating an upload resource for debug symbols.");

        final CompletableFuture<UploadRequest> future = new CompletableFuture<>();

        factory.createAppCenterService()
            .symbolUploadsCreate(request.ownerName, request.appName, symbolUploadRequest)
            .whenComplete((symbolsUploadBeginResponse, throwable) -> {
                if (throwable != null) {
                    final AppCenterException exception = logFailure("Create upload resource for debug symbols unsuccessful: ", throwable);
                    future.completeExceptionally(exception);
                } else {
                    log("Create upload resource for debug symbols successful.");
                    final UploadRequest uploadRequest = request.newBuilder()
                        .setSymbolUploadUrl(symbolsUploadBeginResponse.upload_url)
                        .setSymbolUploadId(symbolsUploadBeginResponse.symbol_upload_id)
                        .build();
                    future.complete(uploadRequest);
                }
            });

        return future;
    }

    @Override
    public PrintStream getLogger() {
        return taskListener.getLogger();
    }
}