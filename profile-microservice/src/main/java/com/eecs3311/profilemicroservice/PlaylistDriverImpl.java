package com.eecs3311.profilemicroservice;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitPlaylistDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nPlaylist:playlist) ASSERT exists(nPlaylist.plName)";
				trans.run(queryStr);
				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Playlist constraint already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}

	@Override
	public DbQueryStatus likeSong(String userName, String songId) {
		DbQueryStatus status;
		try {
			Session session = driver.session();
			session.run(
					String.format("MATCH (p:profile {userName: '%s'}), (s:song {songId: '%s'}) CREATE (p)-[:liked]->(s)", userName, songId)
			);
			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e){
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return status;
	}

	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		DbQueryStatus status;
		try {
			Session session = driver.session();
			session.run(
					String.format("MATCH (p:profile {userName: '%s'})-[r:liked]->(s:song {songId: '%s'}) DELETE r", userName, songId)
			);
			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e){
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return status;
	}
}
