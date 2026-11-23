package com.omnia.backend.service.interfaces;

public interface PublicCatalogMediaService {

    /**
     * True when the file is a photo of an ACTIVE product or the logo of an ACTIVE
     * organization, i.e. something any guest browsing the catalog may see.
     */
    boolean isPublicCatalogFile(Long fileId);

    /**
     * True for private documents (seller verification) that only platform admins may
     * read; everyone else gets 404 from /api/files.
     */
    boolean isPrivateDocument(Long fileId);
}
