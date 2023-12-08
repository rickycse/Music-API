package com.eecs3311.profilemicroservice;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.neo4j.driver.v1.*;
import org.springframework.stereotype.Repository;

import java.lang.Record;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {
	// Initialize the Neo4j driver from the ProfileMicroserviceApplication.
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
	/**
	 * Method to like a song. It creates a relationship between a User and a Song node in the Neo4j database.
	 * @param userName The username of the user liking the song.
	 * @param songId The ID of the song being liked.
	 * @return DbQueryStatus The status of the database query including any error or success messages.
	 */
	@Override
	public DbQueryStatus likeSong(String userName, String songId) {
		// Initialize the query status with a default success message.
		DbQueryStatus dbQueryStatus = new DbQueryStatus("Like Song", DbQueryExecResult.QUERY_OK);

		try(Session session = driver.session()){
			// Cypher query to create a LIKES relationship between the User and the Song.

			String query = "MATCH (u:User {name: $userName}), (s:Song {id: $songId}) "+
					"MERGE (u)-[:LIKES]->(s) "+
					"RETURN s";

			// Execute the query with the provided parameters.
			StatementResult result = session.run(query, Values.parameters("userName", userName, "songId", songId));

			// Check if the query did not find the user or the song.
			if(!result.hasNext()){
				dbQueryStatus.setdbQueryExecResult(DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				dbQueryStatus.setMessage("User or song not found");
			}else {
				dbQueryStatus.setMessage("Song liked successfully");
			}
		}catch (Exception e){
			// Handle any exceptions by setting the query status to an error.

			dbQueryStatus.setdbQueryExecResult(DbQueryExecResult.QUERY_ERROR_GENERIC);
			dbQueryStatus.setData(e.getMessage());

		}

		return dbQueryStatus;
	}


	/**
	 * Method to unlike a song. It deletes a relationship between a User and a Song node in the Neo4j database.
	 * @param userName The username of the user unliking the song.
	 * @param songId The ID of the song being unliked.
	 * @return DbQueryStatus The status of the database query including any error or success messages.
	 */
	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		// Initialize the query status with a default success message.
		DbQueryStatus dbQueryStatus = new DbQueryStatus("Unlike Song", DbQueryExecResult.QUERY_OK);

		try(Session session = driver.session()){
			// Cypher query to delete the LIKES relationship between the User and the Song.
			String query = "MATCH (u:User {name: $userName})-[r:LIKES]->(s:Song {id: $songId}) " +
					"DELETE r " +
					"RETURN s";
			// Execute the query with the provided parameters.
			StatementResult result = session.run(query, Values.parameters("userName", userName, "songId", songId));

			// Check if the query did not find the like relationship.
			if(!result.hasNext()){
				dbQueryStatus.setdbQueryExecResult(DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				dbQueryStatus.setMessage("Like relationship not found");
			} else {
				dbQueryStatus.setMessage("Song unliked successfully");
			}
		}catch (Exception e){
			// Handle any exceptions by setting the query status to an error.
			dbQueryStatus.setdbQueryExecResult(DbQueryExecResult.QUERY_ERROR_GENERIC);
			dbQueryStatus.setData(e.getMessage());
		}

		return dbQueryStatus;

	}

	@Override
	public DbQueryStatus generateMixedPlaylist(String userName, String friendUserName) {
		ArrayList<Object> userSongs = new ArrayList<>();
		ArrayList<Object> friendSongs = new ArrayList<>();
		DbQueryStatus status = new DbQueryStatus("", DbQueryExecResult.QUERY_OK);
		try {
			String userQuery = String.format("MATCH (:playlist {plName: '%s-favorites'})-[:includes]->(s:song) RETURN s", userName);
			StatementResult userPlaylist = driver.session().run(userQuery);
			// Gets all the user's songs
			while (userPlaylist.hasNext()) {
				Record record = userPlaylist.next();
				Map<String, Object> map = record.get(0).asMap();
				userSongs.add(map.get("songId"));
			}
			// Gets all the user's friend's songs.
			String friendQuery = String.format("MATCH (:playlist {plName: '%s-favorites'})-[:includes]->(s:song) RETURN s", friendUserName);
			StatementResult friendPlaylist = driver.session().run(friendQuery);
			while (friendPlaylist.hasNext()) {
				Record record = friendPlaylist.next();
				Map<String, Object> map = record.get(0).asMap();
				friendSongs.add(map.get("songId"));
			}
			userSongs.addAll(friendSongs);
			Collections.shuffle(userSongs);
		} catch (Exception e){
			e.printStackTrace();
			return new DbQueryStatus("Error with driver session", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		// Gets all the song titles from the two playlists
		ArrayList<String> songTitles = new ArrayList<>();
		for(Object id : userSongs){
			String songUrl = "http://localhost:3001/getSongTitleById/" + id;
			Request request = new Request.Builder().url(songUrl).build();
			try {
				Response response = client.newCall(request).execute();
				if (!response.isSuccessful()) return new DbQueryStatus("Error with driver session", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				JSONObject json = new JSONObject(Objects.requireNonNull(response.body()).string());
				songTitles.add((String) json.get("data"));
			} catch (Exception e) {
				e.printStackTrace();
				return new DbQueryStatus("Error requesting song title with ID: " + id, DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		}
		status.setData(songTitles);
		status.setMessage("Success");
		return status;
	}
}
