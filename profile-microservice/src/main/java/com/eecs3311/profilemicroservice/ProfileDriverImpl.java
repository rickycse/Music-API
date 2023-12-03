package com.eecs3311.profilemicroservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.*;

import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;

@Repository
public class ProfileDriverImpl implements ProfileDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitProfileDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.userName)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.password)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT nProfile.userName IS UNIQUE";
				trans.run(queryStr);

				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Profile constraints already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}
	
	@Override
	public DbQueryStatus createUserProfile(String userName, String fullName, String password) {
		String usernamePlaylist = userName + "-favorites";
		// Start a session
		try (Session session = driver.session()) {
			// Run a query to add profile to Profile database
			String query1 = String.format("CREATE (p:profile {userName: '%s', fullName: '%s', password: '%s'})", userName, fullName, password);
			session.run(query1);
			// Creates userName-favourites playlist
			String query2 = String.format("CREATE (pl:playlist {plName: '%s'})", usernamePlaylist);
			session.run(query2);
			// creates relation '(nProfile:profile)-[:created]->(nPlaylist:playlist)'
			String query3 = String.format("MATCH (p:profile {userName: '%s'}), (pl:playlist {plName: '%s'})\nCREATE (p)-[:created]->(pl)", userName, usernamePlaylist);
			session.run(query3);

			return new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	@Override
	public DbQueryStatus followFriend(String userName, String frndUserName) {
		// Start a session
		try (Session session = driver.session()) {
			// follow friend query
			String query1 = String.format("MATCH (p1:profile {userName: '%s'}), (p2:profile {userName: '%s'})\nCREATE (p1)-[:follows]->(p2)", userName, frndUserName);
			session.run(query1);

			return new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {
		try (Session session = driver.session()) {
			// unfollow friend query
			String query1 = String.format("MATCH (p1:profile {userName: '%s'})-[r:follows]->(p2:profile {userName: '%s'}) DELETE r", userName, frndUserName);
			session.run(query1);

			return new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
		// Start a session
		try (Session session = driver.session()) {
			// get a list of all songs liked by friends of 'userName'
			String query1 = String.format("MATCH (:Person {userName: '%s'})-[:follows]->(friend:Person)-[:liked-by]->(song:Song)\nRETURN song.songName as likedSong", userName);
			StatementResult result = session.run(query1);

			// store all retrieved friend's liked songs from query into a list
			List<String> likedSongs = new ArrayList<>();
			while (result.hasNext()) {
				Record record = result.next();
				likedSongs.add(record.get("likedSong").asString());
			}

			return new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
}
