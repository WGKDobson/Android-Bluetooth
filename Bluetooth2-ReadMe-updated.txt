Bluetooth2 - ReadMe
CSC6360 Graduate Project - Spring 2021
William Dobson 4-14-21

This app will connect and communicate over Bluetooth with a Raspberry Pi 4 (RPi4) 
running simple python server code appended at the bottom of this document.
This server code must be up and running on the RPi4 before attempting to connect.
Use the command:  
                      sudo python3 filename.py   
to start the server.

When running the App list the devices with the DISCOVER button and select 'raspberrypi' 
from the device list. If this is not successful try Pairing using your phone's built-in
USB manager and use the PAIRED button to connect.

Once connected you may use the LED Check Boxes to toggle the GPIO ports 21, 20, and 16 
on the RPi4 and the GET TEMP should return the CPU core temperature of that system.

----------------------------------------------------------------------------------
# Pybluez simple python bluetooth server example modified for use in this project
# Updated 4-14-21

from bluetooth import *
from time import sleep
import RPi.GPIO as GPIO        #calling for header file which helps in using GPIOs of PI
from gpiozero import LED
from gpiozero import CPUTemperature

cpu = CPUTemperature()
print(cpu.temperature)
gled=LED(21)
yled=LED(20)
rled=LED(16)
 
server_sock=BluetoothSocket( RFCOMM )
server_sock.bind(("", 1)) #PORT_ANY))
server_sock.listen(1)

port = server_sock.getsockname()[1]

uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

advertise_service( server_sock, "RPI-SerialPortServer",
                   service_id = uuid,
                   service_classes = [ uuid, SERIAL_PORT_CLASS ],
                   profiles = [ SERIAL_PORT_PROFILE ], 
#                   protocols = [ OBEX_UUID ] 
                    )
while True :                   
    print("Waiting for connection on RFCOMM channel %d" % port)

    client_sock, client_info = server_sock.accept()
    print("Accepted connection from ", client_info)

    try:
        while True:
            data = client_sock.recv(1024)
            if len(data) == 0: break


            if  "1" in str(data):
                gled.toggle()
                client_sock.send(bytes("toggle:Green="+str(GPIO.input(21))+"\n", 'utf-8'))
            elif "2" in str(data):
                yled.toggle()
                client_sock.send(bytes("toggle:Yellow="+str(GPIO.input(20))+"\n", 'utf-8'))
            elif "3" in str(data):
                rled.toggle()
                client_sock.send(bytes("toggle:Red="+str(GPIO.input(16))+"\n", 'utf-8'))
            elif "4" in str(data):
                client_sock.send(bytes("CPU Temp="+str(cpu.temperature)+" Celcius\n", 'utf-8'))
                rled.toggle()
                sleep(0.25)
                rled.toggle()
                sleep(0.25)
                yled.toggle()
                sleep(0.25)
                yled.toggle()
                sleep(0.25)
                gled.toggle()
                sleep(0.25)
                gled.toggle()
                sleep(0.25)
                
            else :
                client_sock.send(data)

            print("received [%s]" % data)
    except IOError:
        pass

print("disconnected")

client_sock.close()
server_sock.close()
print("all done")
