/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/* eslint-disable max-len */

//
// |-------|--------------|------------------------------------|------|
//                                                                                         _
//          ______________            ___________________               _                  |
//         | SERVICE NAME |          |   DURATION BAR    |              |  spanBarHeight   | spanBarRowHeight
//          --------------            -------------------               -                  |
//                                                                                         -
//
// |-------|                                                   |------|
//   spanTreeWidthPercent                                        timelineRightMarginPercent
//         |--------------|
//           serviceNameWidthPercent
//                        |------------------------------------|
//                          timelineWidthPercent
//
export const spanTreeWidthPercent = 10; // %
export const serviceNameWidthPercent = 18; // %
export const timelineRightMarginPercent = 2; // %
export const timelineWidthPercent =
  100 -
  (spanTreeWidthPercent + serviceNameWidthPercent + timelineRightMarginPercent);
export const spanBarRowHeight = 30; // px
export const spanBarHeight = spanBarRowHeight - 4; // px;

export const spanTreeLineWidthPercentPerDepth = (depth) =>
  spanTreeWidthPercent / (depth + 1); // %
export const serviceNameBadgeWidth = serviceNameWidthPercent - 2;
export const serviceNameBadgeHeight = 20;
export const serviceNameBadgeTranslate = `translate(16,${
  -serviceNameBadgeHeight / 2
})`; // px
export const spanToggleButtonLengthOfSide = 16; // px
export const spanToggleButtonTranslate = `translate(${
  -spanToggleButtonLengthOfSide / 2
},${-spanToggleButtonLengthOfSide / 2})`; // px

//
//                                                             _     _    _
//                                                             |     |    |
//                                                             | A's spanBarRowOffsetY
//                                                             |     |    |
//            _____________________________                    _     |    |
// ----------|            SPAN A           |-----------------        |    |
//            -----------------------------                          |    |
//                                                                   | B's spanBarRowOffsetY
//                       ______________________________              _    |
// ---------------------|            SPAN B            |------            - B's spanBarLinePosY
//                       ------------------------------
//
//                      |------------------------------|
//                           B's spanBarWidthPercent
// |--------------------|
//   spanBarOffsetXPercent
//
export const spanBarRowOffsetY = (index) => index * spanBarRowHeight; // px
export const spanBarOffsetY = (index) => spanBarRowOffsetY(index) + 2; // px
export const spanBarLinePosY = (index) =>
  spanBarRowOffsetY(index) + spanBarRowHeight / 2; // px
export const spanBarWidthPercent = (width) =>
  timelineWidthPercent * (width / 100); // %
export const spanBarOffsetXPercent = (left) =>
  spanTreeWidthPercent +
  serviceNameWidthPercent +
  timelineWidthPercent * (left / 100); // %

export const timelineHeight = (spanCounts) => spanBarRowHeight * spanCounts; // px
