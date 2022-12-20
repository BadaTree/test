import cv2
import numpy as np
from PIL import Image
import sys
import json
import os

LOCATION = '하나스퀘어'
PACKAGENAME = 'sequentialForSuwonLightVersion'
file_path = f'android/{PACKAGENAME}/app/src/main/assets/images/file_list.json'

def flatten(input):
    if input == '0':
        return False
    else:
        return True
        
def get_boundary(data_list,height,width):
    try:
        # frame
        if height == len(data_list) -1 or width == len(data_list[height]) or height == 0 or width == 0:
            return [0,0,0]

        # edge
        if data_list[height][width+1] and data_list[height][width] == False:
            return [0,0,0]
        if data_list[height][width-1] and data_list[height][width] == False:
            return [0,0,0]
        if data_list[height+1][width] and data_list[height][width] == False:
            return [0,0,0]
        if data_list[height-1][width] and data_list[height][width] == False:
            return [0,0,0]
        
        # empty space
        if data_list[height][width] == False:
            return[236,206,165]   # blue code
        else:
            return [255,255,255]
    except:
        return [0,0,0]
    return [255,255,255]

def get_image():
    DIR = f"./WIFIengine (2)/mag/{LOCATION}/"

    file_list = os.listdir(DIR)
    json_data = {}
    
    for file in file_list:
        l = []
        data_list = []
        boundary_img = []
        with open(DIR + file , "r") as f:
            for line in f:
                l = list(map(flatten ,line.split('\t')))
                l = l[:-1]
                data_list.append(l)

            
            for height in range(len(data_list)):
                b = []  
                for width in range(len(data_list[height])):
                    b.append(get_boundary(data_list,height,width))
                boundary_img.append(b)


        data = np.array(boundary_img)  
        file = file.replace('.txt', '')
        try:
            cv2.imwrite(f'../../../android/{PACKAGENAME}/app/src/main/assets/images/map/ggggg.png', data)
            json_data[file] = file
        except Exception as e:
            print(e)
        cv2.waitKey()
        cv2.destroyAllWindows()
    with open(file_path,'w') as outfile:
        json.dump(json_data, outfile, indent='\t')

if __name__ =='__main__':
    get_image()