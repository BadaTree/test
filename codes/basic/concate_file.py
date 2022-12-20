import os

dir = f'./WIFIengine (2)/data/suwon copy/train/train_0/19_time/'

file_list = os.listdir(dir)
datas = ''
for file in file_list:
    if file == 'total.txt':
        break
    with open(dir + file, 'rt', encoding='UTF-8') as f:
        lines = f.readlines()
        for line in lines:
            datas += line
            
f = open(dir+'total.txt','w')

f.write(datas)
f.close()

    