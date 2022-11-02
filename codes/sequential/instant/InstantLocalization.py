import time
from math import *
# from codes.sequential.instant.InstantParticle_Mother import  InstantParticle_Mother
# from codes.sequential.instant.Map import Map
from instant.InstantParticle_Mother import InstantParticle_Mother
from instant.Map import Map

class InstantLocalization():
    def __init__(self, location_name, area_num = 1):
        # 맵파일 읽기
        self.instant_result = {"status_code":-1.0, "gyro_from_map":-1.0, "pos_x":-1.0, "pos_y":-1.0}
        self.mapVector_temp = []
        f = open(f"./자기장맵/{location_name}/handhashmap_for_instant_3.txt", "r")
        while True:
            row = f.readline()
            if not row:
                break
            self.mapVector_temp.append(list(map(float, row.split("\t"))))
        self.mapVector = Map(f"./자기장맵/{location_name}/handhashmap.txt")

        # 필요 변수들
        self.angleList = [i for i in range(0, 360, 10)]
        self.sampled_sequence_average_list = []
        self.cur_step = -1
        self.sampled_vector_magnitude = 0
        self.instant_particle_mother_list = []
        self.pre_state = 0 # 0 : ON HAND, 1 : IN POCKET, 2 : SWING
        self.cur_state = 0

        # ######### 포스코센터 ##########
        # self.vector_threshold = 5.0
        # self.vector_threshold_second = 3.0
        # self.magnitude_threshold = 3.0
        # self.firstThreshold = 7
        # self.weight_according_step = 0.5
        # self.vector_weight = 0.5
        # self.take_n_mother = 36
        # self.early_stop_in_n_mother = 10
        # ################################

        ## 하나스퀘어_wifi 최적화 parameter ##
        self.early_stop_in_n_mother = 10
        self.take_n_mother = 36
        self.vector_weight = 0.5
        self.weight_according_step = 0.12
        self.magnitude_threshold = 4.0
        self.vector_threshold_second = 7.0
        self.vector_threshold = 12.0
        self.firstThreshold = 12.0

        self.init_range = [0, 0, 0, 0]
        self.refx = 0.0
        self.refy = 0.0
        self.area_num = area_num

        self.wifi = WiFi(location_name)


    def find_range(self):
        self.init_range = self.wifi.find_range(self.refx, self.refy)


    def check_in_range(self, x, y, i):
        x = float(x)
        y = float(y)

        result = False
        if (self.init_range[i][0] <= x) and (x <= self.init_range[i][1]) and (self.init_range[i][2] <= y) and(y <= self.init_range[i][3]):
            result = True
        return result


    def reset_all(self):
        # 필요 변수들
        self.angleList = [i for i in range(0, 360, 10)]
        self.sampled_sequence_average_list = []
        self.cur_step = -1
        self.sampled_vector_magnitude = 0.0
        self.instant_particle_mother_list = []
        self.instant_result = {"status_code":-1.0, "gyro_from_map":-1.0, "pos_x":-1.0, "pos_y":-1.0}

    def init_for_always_on_mode(self, cur_gyro):
        cur_gyro = int(float(cur_gyro))
        self.angleList = [i for i in range((360-cur_gyro)-90, (360-cur_gyro)+91, 10)]
        self.sampled_sequence_average_list = []
        self.cur_step = -1
        self.sampled_vector_magnitude = 0.0
        self.instant_particle_mother_list = []
        self.instant_result = {"status_code":-1.0, "gyro_from_map":-1.0, "pos_x":-1.0, "pos_y":-1.0}


    def __first_matching_with_map_and_create_mothers(self):
        for i in range(len(self.angleList)):
            self.instant_particle_mother_list.append(InstantParticle_Mother(self.angleList[i]))
            for row in self.mapVector_temp:
                if self.check_in_range(row[0], row[1]):
                    self.instant_particle_mother_list[-1].appendChildren(row[:2], row[2:])



    def getLocation(self, stepLength, gyro):
        if self.instant_result["status_code"] == 200.0:
            self.init_for_always_on_mode(float(self.instant_result["gyro_from_map"]) + float(gyro))
        elif self.instant_result["status_code"] == 400.0:
            self.reset_all()

        self.cur_step += 1

        if self.cur_step == 0:
            for mother in self.instant_particle_mother_list:
                self.moveChildren(mother, stepLength, gyro)
            self.__first_matching_with_map_and_create_mothers(self.vectorList)
            self.instant_result["status_code"] = 100.0

            idx = 0
            for mother in sorted(self.instant_particle_mother_list, key=lambda x:len(x.particle_children_list), reverse=True):
                print(mother.my_angle, len(mother.particle_children_list))
                idx += 1
                if idx == 5:
                    break

            return self.instant_result

        cur_idx = -1
        while 1:
            cur_idx += 1
            if len(self.instant_particle_mother_list) == 0:
                self.instant_result["status_code"] = 400.0
                return self.instant_result
            particle_mother = self.instant_particle_mother_list[cur_idx]
            self.moveChildren(particle_mother, stepLength, gyro) # 아이들 움직이기. 움직이자마자 벽에 부딪히는 아이들은 다 죽이기.

            if len(particle_mother.particle_children_list) == 0: # 아이 안 갖고 있는 mother는 삭제.
                self.instant_particle_mother_list.remove(particle_mother)
                cur_idx -= 1

            if cur_idx == len(self.instant_particle_mother_list)-1:
                break

        self.instant_result = self.estimateInitialDirAndPos(self.instant_particle_mother_list, gyro)
        idx = 0

        return self.instant_result


    # 샘플링 된 자기장 벡터를 여러 방향으로 생성. 이 때, sequence의 평균값을 바로 제거 (bias normalization)
    # 여기 들어가는 벡터는 시작 방향을 기준으로 벡터 캘리브레이션 된 값.


    def cal_difference_angle(self, a, b):
        result1 = (abs(a - b) + 360) % 360
        result2 = 360 - result1
        if result1 <= result2:
            return result1
        else:
            return result2

    def estimateInitialDirAndPos(self, mother_list, gyro):
        num_of_mother = len(mother_list)

        if num_of_mother >= 3:
            if num_of_mother <= self.early_stop_in_n_mother: # 엄마가 3개이상 n개이하일 때, early stop 동작
                # TODO : 상위 5개 mother끼리 다시 instant 돌리기. 성공률 높이기 위함!
                # Do Something!
                sorted_mother = sorted(self.instant_particle_mother_list, key=lambda x: (len(x.particle_children_list), x.getAvgWeight()), reverse=True)
                first_mother = sorted_mother[0]
                second_mother = sorted_mother[1]

                if len(second_mother.particle_children_list) >= 1:
                    # first_mother와 second_mother가 친한 엄마라면, 강남 엄마 알고리즘 동작
                    if self.cal_difference_angle(first_mother.my_angle, second_mother.my_angle) <= 10:
                        first_mother_answer = self.calculate_answer_position(first_mother)
                        second_mother_answer = self.calculate_answer_position(second_mother)
                        if (first_mother_answer == (-1, -1) or second_mother_answer == (-1, -1)):
                            return {"status_code":100.0, "gyro_from_map":-1.0, "pos_x":-1.0, "pos_y":-1.0}

                        else:
                            distance_with_two_answer = sqrt((first_mother_answer[0]-second_mother_answer[0])**2 + (first_mother_answer[1]-second_mother_answer[1])**2) * 0.1
                            if distance_with_two_answer <= 1.5:
                                answer_dir_temp = (first_mother.my_angle + second_mother.my_angle) / 2 if (abs(first_mother.my_angle - second_mother.my_angle) != 350) else (first_mother.my_angle + second_mother.my_angle + 360) / 2
                                answer_dir = (((360 - answer_dir_temp)+ gyro)+360)%360
                                answer_x = (first_mother_answer[0] + second_mother_answer[0]) / 2
                                answer_y = (first_mother_answer[1] + second_mother_answer[1]) / 2
                                return {"status_code":200.0, "gyro_from_map":answer_dir, "pos_x":answer_x, "pos_y":answer_y}
                            else:
                                return {"status_code": 100.0, "gyro_from_map": -1.0, "pos_x": -1.0, "pos_y": -1.0}
                    # 엄마 하나 빼고, 나머지 엄마들의 자식이 하나밖에 남지 않았다면 early stop 동작
                    elif (len(second_mother.particle_children_list) == 1) and (len(first_mother.particle_children_list) != 1):
                        best_mother = first_mother
                    else:
                        return {"status_code": 100.0, "gyro_from_map": -1.0, "pos_x": -1.0, "pos_y": -1.0}

                else:
                    return {"status_code": 100.0, "gyro_from_map": -1.0, "pos_x": -1.0, "pos_y": -1.0}
            else:
                return {"status_code": 100.0, "gyro_from_map": -1.0, "pos_x": -1.0, "pos_y": -1.0}

        elif num_of_mother == 2:
            weight_sum_list = []
            for mother in mother_list:
                weight_sum = 0.0
                for children in mother.particle_children_list:
                    weight_sum += children.weight
                weight_sum_list.append(weight_sum)
            if weight_sum_list[0] >= weight_sum_list[1]:
                best_mother = mother_list[0]
            else:
                best_mother = mother_list[1]

        elif num_of_mother == 1:
            best_mother = mother_list[0]
        elif num_of_mother == 0:
            return {"status_code": 400.0, "gyro_from_map": -1.0, "pos_x": -1.0, "pos_y": -1.0}

        # 혹시 모를 에러를 방지
        num_of_children = len(best_mother.particle_children_list)
        if num_of_children == 0:
            return {"status_code": 400.0, "gyro_from_map": -1.0, "pos_x": -1.0, "pos_y": -1.0}
        # 정답 좌표 계산
        answer_x, answer_y = self.calculate_answer_position(best_mother)
        answer_dir = str((((360 - int(best_mother.my_angle)) + gyro) + 360) % 360)
        if (answer_x, answer_y) == (-1, -1): # 방향만 수렴
            return {"status_code": 101.0, "gyro_from_map": answer_dir, "pos_x": -1.0, "pos_y": -1.0}
        else:
            return {"status_code": 200.0, "gyro_from_map": answer_dir, "pos_x": answer_x, "pos_y": answer_y}

    def calculate_answer_position(self, mother) -> tuple:
        answer_x = 0.0
        answer_y = 0.0
        for children in mother.particle_children_list:
            answer_x += children.x
            answer_y += children.y
        num_of_children = len(mother.particle_children_list)
        answer_x = answer_x / num_of_children
        answer_y = answer_y / num_of_children
        dist_avg = 0
        for children in mother.particle_children_list:
            dist_avg += sqrt(((answer_x - children.x) ** 2 + (answer_y - children.y) ** 2)) * 0.1
        dist_avg = dist_avg / len(mother.particle_children_list)
        if dist_avg > 1.5:
            return (-1, -1)
        return (answer_x, answer_y)

    def moveChildren(self, particle_mother, step_length, gyro):
        cur_idx = -1
        while 1:
            gyro_result = gyro
            cur_idx += 1
            if cur_idx == len(particle_mother.particle_children_list):
                break
            children = particle_mother.particle_children_list[cur_idx]
            children.x -= step_length * 10 * sin((particle_mother.my_angle - gyro_result) * pi / 180)
            children.y += step_length * 10 * cos((particle_mother.my_angle - gyro_result) * pi / 180)

            # 벽에 부딪혔는지 검사. 부딪혔으면 바로 죽이기.
            if not self.mapVector.isPossiblePosition(children.x, children.y):
                particle_mother.removeChildren(cur_idx)
                cur_idx -= 1

    def checkChildren(self, particle_mother):
        cur_idx = -1
        while 1:
            cur_idx += 1
            if cur_idx == len(particle_mother.particle_children_list):
                break
            children = particle_mother.particle_children_list[cur_idx]
            # 벽에 부딪혔는지 검사. 부딪혔으면 바로 죽이기.
            if not self.check_in_range(children.x, children.y, int(particle_mother.my_angle / 10)):
                particle_mother.removeChildren(cur_idx)
                cur_idx -= 1