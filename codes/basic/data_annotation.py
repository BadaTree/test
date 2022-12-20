

data_list = list()
data_dict = dict()
max_x = 0.0
max_y = 0.0
# with open(f"./WIFIengine (2)/data/suwon copy/train/10_time/total.txt", 'r') as f:
#     for line in f.readlines:
#         # data_list.append(line.replace('\n', '').split('\t'))
#         max_x = max(max_x, data_list[1])
#         max_y = max(max_y, data_list[2])
#         if str(data_list[1])+'/'+str(data_list[2]) in data_dict:
#             data_list = data_dict[str(data_list[1])+'/'+str(data_list[2])]
#             data_list.append([data_list[4], data_list[5]])
        
#         else:
#             data_list = [data_list[4], data_list[5]]

#         data_dict[str(data_list[1])+'/'+str(data_list[2])] = data_list

data_list = list()
max_x = 0.0
max_y = 0.0
with open(f"./WIFIengine (2)/data/suwon copy/train/13_time/total.txt", 'r') as f:
    for line in f.readlines:
        data_list.append(line.replace('\n','').split('\t'))
        max_x = max(max_x, data_list[1])
        max_y = max(max_y, data_list[2])
