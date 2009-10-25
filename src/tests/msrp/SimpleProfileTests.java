/*
 * Copyright � Jo�o Antunes 2008 This file is part of MSRP Java Stack.
 * 
 * MSRP Java Stack is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * MSRP Java Stack is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with MSRP Java Stack. If not, see <http://www.gnu.org/licenses/>.
 */
package msrp;

import msrp.messages.Message;
import msrp.messages.OutgoingFileMessage;
import msrp.messages.OutgoingMessage;
import msrp.testutils.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Properties;

import msrp.testutils.MockMSRPSessionListener;
import msrp.utils.TextUtils;

import org.junit.*;

/**
 * 
 * Simple tests built for speed and to be used by the eclipse profiler (TPTP)
 * 
 * @author Jo�o Andr� Pereira Antunes
 * 
 */
public class SimpleProfileTests
{
    InetAddress address;

    Session sendingSession;

    Session receivingSession;

    MockMSRPSessionListener receivingSessionListener =
        new MockMSRPSessionListener("receivingSessionListener");

    MockMSRPSessionListener sendingSessionListener =
        new MockMSRPSessionListener("sendinSessionListener");

    File tempFile;

    String tempFileDir;

    @Before
    public void setUpConnection()
    {
        /* fetch the address to which this sessions are going to be bound: */
        Properties testProperties = new Properties();
        try
        {

            /* Set the limit to be of 30 MB of messages allowed in memory */
            MSRPStack.setShortMessageBytes(30024 * 1024);

            testProperties.load(TestReportMechanism.class
                .getResourceAsStream("/test.properties"));
            String addressString = testProperties.getProperty("address");
            /*
             * checks if we want the temp files on a specific directory. if the
             * propriety doesn't exist the default dir used by the JVM is used
             */
            tempFileDir = testProperties.getProperty("tempdirectory");
            address = InetAddress.getByName(addressString);
            sendingSession = new Session(false, false, address);
            receivingSession =
                new Session(false, false, sendingSession.getURI(), address);

            receivingSession.addMSRPSessionListener(receivingSessionListener);
            sendingSession.addMSRPSessionListener(sendingSessionListener);
            if (tempFileDir != null)
            {
                System.out.println("Using temporary file directory: "
                    + tempFileDir);
                tempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, new File(tempFileDir));
            }
            else
                tempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, null);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    @After
    public void tearDownConnection()
    {
        // TODO needs: tear down of the sessions
        // TODO needs: (?!) timer to mantain connection active even though
        // sessions
        // are over (?!)
        tempFile.delete();
    }

    /**
     * Tests sending a 300KB Message with a MemoryDataContainer to a
     * MemoryDataContainer
     */
    @Test
    public void test300KBMessageMemoryToMemory()
    {
        try
        {

            byte[] smallData = new byte[300 * 1024];
            TextUtils.generateRandom(smallData);

            Message threeHKbMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            threeHKbMessage.setSuccessReport(false);

            /* connect the two sessions: */

            ArrayList<URI> toPathSendSession = new ArrayList<URI>();
            toPathSendSession.add(receivingSession.getURI());

            sendingSession.addToPath(toPathSendSession);

            /*
             * message should be transfered or in the process of being
             * completely transfered
             */

            /* make the mocklistener accept the message */
            synchronized (receivingSessionListener)
            {
                DataContainer dc = new MemoryDataContainer(300 * 1024);
                receivingSessionListener.setDataContainer(dc);
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait(9000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't worked and the message didn't got "
                    + "accepted");

            synchronized (receivingSessionListener)
            {
                /*
                 * allow the message to be received
                 */
                receivingSessionListener.wait();
            }

            ByteBuffer receivedByteBuffer =
                receivingSessionListener.getReceiveMessage().getDataContainer()
                    .get(0, 0);
            byte[] receivedData = receivedByteBuffer.array();
            /*
             * assert that the received data matches the sent data
             */
            assertArrayEquals(smallData, receivedData);

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    /**
     * Similar to the TestSendingExistingFile test, but doesn't has the JUnit
     * asserts at the end.
     * The main purpose is to provide the profiler with a test that spends the
     * runtime just sending the file. So that the asserts don't influence the 
     * execution-time statistics 
     */
    public void testSendingExistingFileToFile()
    {
        /* set up the sending session to send the existing file: */
        File fileToSend = new File("resources/tests/fileToSend");
        assertTrue("Error, file: fileToSend doesn't exist, expected in: "
            + fileToSend.getAbsolutePath(), fileToSend.exists());

        Message messageToBeSent = null;
        try
        {
            messageToBeSent =
                new OutgoingFileMessage(sendingSession,
                    "prs.genericfile/prs.rawbyte", fileToSend);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
        messageToBeSent.setSuccessReport(true);

        /* add the listener */
        sendingSession.addMSRPSessionListener(sendingSessionListener);

        /*
         * set up the receiving session handler to receive the data to the
         * receivedFile
         */

        File receivedFile = new File("receivedFile");

        FileDataContainer receivedFileDC = null;
        try
        {
            receivedFileDC = new FileDataContainer(receivedFile);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
        receivingSessionListener.setDataContainer(receivedFileDC);

        receivingSession.addMSRPSessionListener(receivingSessionListener);

        /* start the transfer by adding the toPath to the sendingSession */
        ArrayList<URI> toPathSendSession = new ArrayList<URI>();
        toPathSendSession.add(receivingSession.getURI());
        try
        {
            sendingSession.addToPath(toPathSendSession);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }

        /*
         * message should be transfered or in the process of being completely
         * transfered
         */

        try
        {
            /* make the mocklistener accept the message */
            synchronized (receivingSessionListener)
            {
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait(10000);
            }

            // confirm that the message got accepted on the
            // receivingSessionListener
            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock (?!) didn't worked and the message didn't got "
                    + "accepted");

            synchronized (receivingSessionListener)
            {
                /*
                 * allow the message to be received
                 */
                receivingSessionListener.wait();
            }
            // stop further validations here as this is the test used for the
            // profiler

        }
        catch (InterruptedException e)
        {
            fail(e.getMessage());
        }

    }

}
