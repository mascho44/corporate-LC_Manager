package de.corporate.lc.check.service;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ReviewMigrationTest {
    @Test void migrationRetainsLegacyRowsAndSeparatesFindingVersions() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:reviewmigration;MODE=PostgreSQL")){
            var sql=connection.createStatement();
            sql.execute("create table letter_of_credit(id uuid primary key)");
            apply(connection,"V11__document_check_decisions.sql");
            sql.execute("insert into letter_of_credit values ('00000000-0000-0000-0000-000000000001')");
            insert(connection,"00000000-0000-0000-0000-000000000002",null,false);
            apply(connection,"V41__versioned_review_decisions.sql");
            try(var rows=sql.executeQuery("select finding_fingerprint,rule_version from document_check_decision")){
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isNull();assertThat(rows.getString(2)).isNull();
            }
            insert(connection,"00000000-0000-0000-0000-000000000003","a".repeat(64),true);
            insert(connection,"00000000-0000-0000-0000-000000000004","b".repeat(64),true);
            assertThatThrownBy(()->insert(connection,"00000000-0000-0000-0000-000000000005","a".repeat(64),true)).isInstanceOf(SQLException.class);
            try(var rows=sql.executeQuery("select count(*) from document_check_decision")){rows.next();assertThat(rows.getInt(1)).isEqualTo(3);}
        }
    }
    private void apply(Connection connection,String migration)throws Exception{
        try(var stream=getClass().getResourceAsStream("/db/migration/"+migration)){
            assertThat(stream).isNotNull();
            String source=new String(stream.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^--[^\r\n]*","");
            for(String statement:source.split(";"))if(!statement.isBlank())connection.createStatement().execute(statement);
        }
    }
    private void insert(Connection connection,String id,String fingerprint,boolean versioned)throws SQLException{
        String columns="id,lc_id,finding_code,document_name,decision,reviewed_by,reviewed_at"+(versioned?",finding_fingerprint":"");
        String values="?,'00000000-0000-0000-0000-000000000001','TEST','invoice.pdf','ACCEPTED','checker',current_timestamp"+(versioned?",?":"");
        try(var query=connection.prepareStatement("insert into document_check_decision("+columns+") values("+values+")")){
            query.setString(1,id);if(versioned)query.setString(2,fingerprint);query.executeUpdate();
        }
    }
}
