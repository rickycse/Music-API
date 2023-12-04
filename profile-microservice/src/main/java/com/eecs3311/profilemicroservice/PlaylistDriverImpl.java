package com.eecs3311.profilemicroservice;

import okhttp3.*;
import org.json.JSONObject;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;

import java.util.HashMap;
import java.util.Objects;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	OkHttpClient client = new OkHttpClient();

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
//			// Sends a GET request to check if the Song exists
//			try {
//				StatementResult result = session.run(String.format("MATCH (s:song {songId:'%s'}) RETURN s", songId));
//				// Creates the Song if it does not already exist in Neo4j database
//				if (!result.hasNext()) {
//					session.run(String.format("CREATE (s:song {songId: '%s'})", songId));
//				}
//			} catch (Exception e){
//				return new DbQueryStatus("Failed adding new Song to database", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
//			}

			// Sends a POST request to update Song favorites count
			String postUrl = "http://localhost:3001/updateSongFavouritesCount";
			String payload = String.format("{\"songId\": \"%s\", \"shouldDecrement\": \"false\"}", songId);
			MediaType JSON = MediaType.get("application/json; charset=utf-8");
			RequestBody body = RequestBody.create(payload, JSON);
			Request postRequest = new Request.Builder()
					.url(postUrl)
					.put(body)
					.build();
			try (Response response = client.newCall(postRequest).execute()){
				if(!response.isSuccessful()){
					return new DbQueryStatus("1. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}
			} catch (Exception e) {
				return new DbQueryStatus("2. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
			// Creates an includes relationship between the user's playlist and song.
			session.run(String.format("MATCH (p:playlist {plName: '%s-favorites'}), (s:song {songId: '%s'}) CREATE (p)-[:includes]->(s)", userName, songId));
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
			// Sends a POST request to decrement the Song's favorites
			String postUrl = "http://localhost:3001/updateSongFavouritesCount";
			MediaType JSON = MediaType.get("application/json; charset=utf-8");
			String payload = String.format("{\"songId\": \"%s\", \"shouldDecrement\": \"true\"}", songId);
			RequestBody body = RequestBody.create(payload, JSON);
			Request postRequest = new Request.Builder()
					.url(postUrl)
					.put(body)
					.build();
			try (Response response = client.newCall(postRequest).execute()){
				if(!response.isSuccessful()){
					return new DbQueryStatus("1. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}
			} catch (Exception e) {
				return new DbQueryStatus("2. Failed POST request to " + postUrl, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
			// Deletes the includes relationship between the user's playlist and song.
			session.run(String.format("MATCH (p:playlist {plName: '%s-favorites'})-[r:includes]->(s:song {songId: '%s'}) DELETE r", userName, songId));
			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e){
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return status;
	}
}
