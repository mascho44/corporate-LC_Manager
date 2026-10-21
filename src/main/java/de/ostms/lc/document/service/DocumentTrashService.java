package de.ostms.lc.document.service;

import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Trash of deleted documents: list, restore and final removal after the retention period. */
@Service
public class DocumentTrashService {
    public static final int RETENTION_DAYS = 30;
    public record TrashItem(UUID id, String filename, String documentType, LocalDateTime deletedAt) {}
    private final JdbcTemplate jdbc;
    public DocumentTrashService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<TrashItem> list(UUID lcId) {
        return jdbc.query("select id, original_filename, document_type, deleted_at from lc_document where tenant_id=? and lc_id=? and deleted_at is not null order by deleted_at desc",
                (rs, i) -> new TrashItem(rs.getObject("id", UUID.class), rs.getString("original_filename"), rs.getString("document_type"), rs.getTimestamp("deleted_at").toLocalDateTime()),
                TenantContext.currentId(), lcId);
    }

    /** Returns the file name of the restored document. */
    @Transactional
    public String restore(UUID lcId, UUID id) {
        var names = jdbc.queryForList("select original_filename from lc_document where tenant_id=? and lc_id=? and id=? and deleted_at is not null", String.class, TenantContext.currentId(), lcId, id);
        if (names.isEmpty()) throw new NoSuchElementException("Document not in trash: " + id);
        jdbc.update("update lc_document set deleted_at=null where tenant_id=? and lc_id=? and id=?", TenantContext.currentId(), lcId, id);
        return names.get(0);
    }

    /** Removes documents that have been in the trash longer than the retention period (current tenant). */
    @Transactional
    public int purgeExpired(LocalDateTime now) {
        return jdbc.update("delete from lc_document where tenant_id=? and deleted_at is not null and deleted_at < ?", TenantContext.currentId(), java.sql.Timestamp.valueOf(now.minusDays(RETENTION_DAYS)));
    }
}
