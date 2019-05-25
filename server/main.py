#!/usr/bin/python

"""
Authors: Gustav Nelson Schneider, Marcus Ostling and Tomas More
"""

import socket
import threading

from time import sleep
from random import randint

import imagetypes as it

"""
The protocol used for the client-server communication is very simple:
first one flag byte is indicating the image type of the data eg. jpg.
The flag byte is followed by 4 bytes indicating the size of the image.
When the client has no more images to send a package containing a 0 as
flag will be sent. The client will at the moment always send a jpg.
"""

TCP_IP = '127.0.0.1'
TCP_PORT = 1338

def start_tcp_server(tcp_ip, tcp_port):
    '''Listen for connections and spawn a thread to handle each connection'''
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind((tcp_ip, tcp_port))
    s.listen(0)
    while True:
        conn, addr = s.accept()
        threadHandler = TCP_thread(conn, addr)
        threadHandler.start()        
        
class TCP_thread(threading.Thread):
    def __init__(self, conn, addr):
        threading.Thread.__init__(self)
        self.conn = conn
        self.addr = addr
        self.currently_recieving = True
        
    def run(self):
        while self.currently_recieving:
            try:
                self.recieve()
            except:
                break
        self.conn.close()

    def recieve(self):
        b = self.read_data(1)      
        flag = int.from_bytes(b, 'big')        
        if (flag == 0):
            #tell backend no more data
            #send back data to user
            self.currently_recieving = False
            response = b"dummy" #this should be the data from the backend
            self.conn.sendall(response)
        elif (flag == it.JPEG):
            print("jpg")
            self.recieve_image(it.JPEG, ".jpg")
        else:
            pass

    def recieve_image(self, img_type, file_ext):
        '''Reads an image as a string and then pass it to the backend to be
        processed'''
        size_field = self.read_data(4)
        size = int.from_bytes(size_field, 'big')
        img_data = self.read_data(size)
        #here the image data can be sent to the back end

    def read_data(self, length):
        '''Reads exactly n bytes of data from the open socket. This should
        probably be rewritten to always read 2*n bytes. this is not a huge
        problem while a non power of two bytes only will be read when there
        is less than 2048 bytes left to read on the socket.
        '''
        data = b""
        left_to_read = length
        while (left_to_read > 0):
            d = self.conn.recv(min(2048, left_to_read))
            left_to_read -= len(d)
            data += d
        return data
        
    def save_image_data(self, data):
        pass
        
if __name__ == '__main__':
    start_tcp_server(TCP_IP, TCP_PORT)
        
