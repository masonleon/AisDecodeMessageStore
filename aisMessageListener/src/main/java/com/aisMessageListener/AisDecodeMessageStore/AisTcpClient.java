package com.aisMessageListener.AisDecodeMessageStore;

import com.aisMessageListener.AisDecodeMessageStore.jdbc.DatabaseConnectionManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;

import dk.tbsalling.aismessages.AISInputStreamReader;
import dk.tbsalling.aismessages.ais.messages.AISMessage;

/**
 * A TCP client that requests AIS messages and passes them to a class for updating the database. The
 * expected server is a Raspberry Pi running a kplex NMEA multiplexer. The multiplexer receives
 * messages from an AIS receiver connected over USB-Serial interface.
 */
class AisTcpClient {

  /**
   * Starts connection with the server on the provided port and requests AIS messages indefinitely.
   * Received messages will be forwarded to a class for database update. Before running, open an ssh
   * tunnel that forwards the desired port from localhost.  Use autossh to keep the tunnel alive.
   *
   * @param portNumber     the port number to bind to.
   * @param connectManager a connection manager that handles access to the database.
   * @throws InterruptedException does not need to be handled. Only occurs when another thread
   *                              interrupts the sleep method during connection recovery.
   */
  void start(int portNumber, DatabaseConnectionManager connectManager) throws InterruptedException {

    while (true) {
      try (// try-with-resources statement - resources will be closed by Java runtime.

           Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), portNumber);
           InputStream messageStream = clientSocket.getInputStream()

      ) {
        AISInputStreamReader streamReader = new AISInputStreamReader(
                messageStream, this::insertMessageIntoDatabase);
        streamReader.run();

      } catch (IOException e) {
        // Wait 5 seconds and attempt to reconnect. If tunnel dropped, autossh will reconnect it.
        System.err.println("Error connecting to kplex server.\n");
        Thread.sleep(5000);
      }
    }
  }

  // TODO: consider AIS exceptions that may be thrown.
  // Consumer method that does stuff with AISmessage classes.  Should use IDatabaseInserter.
  private void insertMessageIntoDatabase(AISMessage message) {
    // Print for testing with testKplexServer.  Can be deleted.
    System.out.println("Received message of type: " + message.getMessageType());
  }

  /**
   * Prints lines to the terminal from the provided BufferedReader.  For testing only.  In
   * production, the input stream will be passed to the AIS decoder library.
   *
   * @param reader the character stream to be read
   * @throws IOException if error occurs while reading
   */
  private static void printBufferedInputStream(BufferedReader reader) throws IOException {

    String line;
    while ((line = reader.readLine()) != null) {
      System.out.println(line);
    }
  }
}
