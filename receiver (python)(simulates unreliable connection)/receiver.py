# -*- coding: utf-8 -*-
import socket
import random
import threading
import sys
from time import sleep, time

# Constants
PACKET_SIZE = 1024
HEADER_SIZE = 2
IP = "127.0.0.1"
FILENAME = "received.png"

def send_ACK(sock, addr, packet_no):
    sock.sendto(packet_no.to_bytes(HEADER_SIZE, byteorder="big"), addr)
    
class DelayedACKThread(threading.Thread):
    def __init__(self, sock, addr, packet_no, max_delay):
        super().__init__()
        
        self.sock = sock
        self.addr = addr
        self.packet_no = packet_no
        self.max_delay = max_delay
        
    def run(self):
        delay = random.random() * self.max_delay / 1000
        sleep(delay)
        
        try:
            send_ACK(self.sock, self.addr, self.packet_no)
        except OSError:
            pass

# Cmd args
PORT = int(sys.argv[1])
N = int(sys.argv[2])
LOSS_PROB = float(sys.argv[3])
DELAY = int(sys.argv[4]) # in ms

# Socket
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((IP, PORT))

# Selective repeat stuff
received_numbers = set()
buf = {} # buffer
rcv_base = 1

# Main loop
sender_addr = None
while True:
    # Receive packet
    if sender_addr is None:
        packet, sender_addr = sock.recvfrom(PACKET_SIZE)
        start_time = time()
    else:
        packet = sock.recv(PACKET_SIZE)
        
    packet_no = int.from_bytes(packet[:HEADER_SIZE], byteorder="big")
    data_bytes = packet[HEADER_SIZE:PACKET_SIZE]
    
    # Uncomment this to print the received packet no and data size (before applying drop)
    #print(packet_no, len(data_bytes))
    
    # Terminate program if packet_no is 0
    if packet_no == 0:
        end_time = time()
        break
    
    # Data packets
    else:
        # Randomly drop some packets
        if random.random() > LOSS_PROB:
            ack_thread = DelayedACKThread(sock, sender_addr, packet_no, DELAY)
                
            if packet_no in range(rcv_base, rcv_base + N):
                ack_thread.start()
                
                # Buffer
                if packet_no not in received_numbers:
                    buf[packet_no] = data_bytes
                    received_numbers.add(packet_no)
                    
                    # Advance rcv_base
                    while rcv_base in received_numbers:
                        rcv_base += 1
                    received_numbers -= set(range(rcv_base))
                        
            elif packet_no in range(rcv_base - N, rcv_base):
                ack_thread.start()
            
sock.close()

# Display time info
print("Time elapsed:", end_time - start_time)

# Save to the disk
print("Writing received file to the disk. Please wait...")
buf_concat = bytes()
for packet_no in sorted(buf):
    buf_concat += buf[packet_no]
    
with open(FILENAME, "wb") as f:
    f.write(buf_concat)
    
