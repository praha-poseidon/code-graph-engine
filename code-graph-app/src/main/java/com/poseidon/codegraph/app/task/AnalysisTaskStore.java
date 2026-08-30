package com.poseidon.codegraph.app.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AnalysisTaskStore {

    private final JdbcTemplate jdbc;
    private final RowMapper<AnalysisTask> rowMapper = (rs, rowNum) -> new AnalysisTask(
        rs.getString("id"),
        rs.getLong("repository_id"),
        rs.getString("status"),
        rs.getInt("progress_current"),
        rs.getInt("progress_total"),
        rs.getString("message"),
        rs.getString("error_details"),
        instant(rs.getTimestamp("created_at")),
        instant(rs.getTimestamp("started_at")),
        instant(rs.getTimestamp("finished_at"))
    );

    public AnalysisTaskStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnalysisTask enqueue(long repositoryId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO analysis_task (id, repository_id, status, message)
            VALUES (?, ?, 'QUEUED', '等待执行')
            """, id, repositoryId);
        return findById(id).orElseThrow();
    }

    public Optional<AnalysisTask> findById(String id) {
        return jdbc.query("SELECT * FROM analysis_task WHERE id = ?", rowMapper, id).stream().findFirst();
    }

    public List<AnalysisTask> findByRepository(long repositoryId) {
        return jdbc.query("""
            SELECT * FROM analysis_task
             WHERE repository_id = ?
             ORDER BY created_at DESC
             LIMIT 50
            """, rowMapper, repositoryId);
    }

    public Optional<AnalysisTask> latestForRepository(long repositoryId) {
        return jdbc.query("""
            SELECT * FROM analysis_task
             WHERE repository_id = ?
             ORDER BY created_at DESC
             LIMIT 1
            """, rowMapper, repositoryId).stream().findFirst();
    }

    public Optional<AnalysisTask> activeForRepository(long repositoryId) {
        return jdbc.query("""
            SELECT * FROM analysis_task
             WHERE repository_id = ? AND status IN ('QUEUED', 'RUNNING')
             ORDER BY created_at DESC
             LIMIT 1
            """, rowMapper, repositoryId).stream().findFirst();
    }

    public Optional<AnalysisTask> claimNext() {
        List<String> ids = jdbc.queryForList("""
            SELECT id FROM analysis_task
             WHERE status = 'QUEUED'
             ORDER BY created_at ASC
             LIMIT 1
            """, String.class);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        String id = ids.getFirst();
        int updated = jdbc.update("""
            UPDATE analysis_task
               SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP, message = '准备工作目录'
             WHERE id = ? AND status = 'QUEUED'
            """, id);
        return updated == 1 ? findById(id) : Optional.empty();
    }

    public int requeueInterrupted() {
        return jdbc.update("""
            UPDATE analysis_task
               SET status = 'QUEUED', started_at = NULL, message = '服务重启，等待重新执行'
             WHERE status = 'RUNNING'
            """);
    }

    public void updateProgress(String id, int current, int total, String message) {
        jdbc.update("""
            UPDATE analysis_task
               SET progress_current = ?, progress_total = ?, message = ?
             WHERE id = ?
            """, current, total, message, id);
    }

    public void succeed(String id, int total) {
        jdbc.update("""
            UPDATE analysis_task
               SET status = 'SUCCEEDED', progress_current = ?, progress_total = ?,
                   message = '分析完成', finished_at = CURRENT_TIMESTAMP
             WHERE id = ?
            """, total, total, id);
    }

    public void fail(String id, String message, String details) {
        jdbc.update("""
            UPDATE analysis_task
               SET status = 'FAILED', message = ?, error_details = ?, finished_at = CURRENT_TIMESTAMP
             WHERE id = ?
            """, message, details, id);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
