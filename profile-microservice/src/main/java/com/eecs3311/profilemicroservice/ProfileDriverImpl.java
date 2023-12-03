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
			session.run("CREATE (p:profile {userName: '$userName', fullName: '$fullName', password: '$password'})", Values.parameters("userName", userName, "$fullName", fullName, "password", password));
			// Creates userName-favourites playlist
			session.run("CREATE (pl:playlist {plName: 'username-favorites'})", values.parameters("username-favorites", usernamePlaylist));
			// creates relation '(nProfile:profile)-[:created]->(nPlaylist:playlist)'
			session.run("MATCH (p:profile {userName: '$username'}), (pl:playlist {plName: '$username-favorites'})\nCREATE (p)-[:created]->(pl)", Values.parameters("username", userName, "username-favorites", usernamePlaylist));

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
			session.run("MATCH (p1:profile {userName: '$userName'}), (p2:profile {userName: '$frndUserName'})\nCREATE (p1)-[:follows]->(p2)");

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
			Result result = session.run("MATCH (:Person {userName: $userName})-[r:follows]->(:Person {friendUserName: $frndUserName}) DELETE r",
					Values.parameters("$userName", "userName", "$frndUserName", "frndUserName"));

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
			Result result = session.run("MATCH (:Person {userName: $userName})-[:follows]->(friend:Person)-[:liked-by]->(song:Song)\nRETURN song.songName as likedSong",
					Values.parameters("$userName", "userName"));

			// store all retrieved friends from query into a list
			List<String> friendList = new ArrayList<>();
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
